package com.fastfood.service.auth;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.PasswordUtil;
import com.fastfood.common.util.SecureToken;
import com.fastfood.common.util.ValidationUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.shared.PasswordResetTokenDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.integration.notification.NotificationSender;
import com.fastfood.integration.notification.NotificationSenders;
import com.fastfood.model.entity.PasswordResetToken;
import com.fastfood.model.entity.User;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Luồng "Quên mật khẩu": người dùng tự lấy lại tài khoản mà không cần quản trị viên.
 * <p>
 * <b>Vì sao vẫn cần, khi đã có chức năng quản trị viên đặt lại hộ.</b> Đặt lại hộ tạo ra một mật
 * khẩu mà hai người biết, phải đọc cho nhau qua điện thoại hay tin nhắn, và chỉ làm được trong
 * giờ có người trực. Luồng này thay bằng một liên kết chỉ chủ hộp thư mở được. Chức năng đặt
 * lại hộ vẫn giữ nguyên cho trường hợp nhân viên mất luôn quyền vào hộp thư công ty.
 * <p>
 * <b>Không nói ra email có tồn tại hay không.</b> Bấm xin đặt lại mật khẩu cho một email bất kỳ
 * đều nhận về cùng một câu trả lời. Khác nhau ở câu trả lời thì trang này trở thành công cụ
 * kiểm tra xem một địa chỉ có phải khách của cửa hàng không — thông tin đó tự nó đã đáng giá,
 * và nó cũng là bước đầu tiên của mọi đợt dò mật khẩu có mục tiêu.
 * <p>
 * <b>Các mốc chặn khác</b> đều nằm ở bảng {@code PasswordResetToken}, xem ghi chú ở lược đồ:
 * chỉ lưu bản băm, dùng một lần, có hạn, và đếm số lần xin trong ít phút gần đây.
 */
public class PasswordResetService {

    private static final Logger LOG = Logger.getLogger(PasswordResetService.class.getName());

    /** Hạn mặc định của một liên kết. Đủ để mở hộp thư, không đủ để một liên kết bị lộ nằm chờ. */
    private static final int DEFAULT_EXPIRY_MINUTES = 15;
    /** Số lần xin tối đa trong một cửa sổ, để không biến chức năng này thành công cụ dội thư. */
    private static final int DEFAULT_MAX_REQUESTS = 3;
    private static final int DEFAULT_REQUEST_WINDOW_MINUTES = 15;

    private final UserDAO userDAO = new UserDAO();
    private final PasswordResetTokenDAO tokenDAO = new PasswordResetTokenDAO();
    private final AuditService auditService = new AuditService();
    private final NotificationSender sender;

    public PasswordResetService() {
        this(NotificationSenders.fromConfig());
    }

    /**
     * Nhận kênh gửi tin từ bên ngoài.
     * <p>
     * Mã đặt lại mật khẩu cố tình không lộ ra ở bất kỳ đâu — không trả về từ {@link #request},
     * không lưu nguyên văn xuống bảng — nên chỗ duy nhất còn nhìn thấy nó là tin nhắn gửi đi.
     * Cho phép thay kênh gửi là cách bài kiểm thử đi đúng con đường của người dùng thật: xin
     * liên kết, nhận tin, mở liên kết. Nó cũng là đường để sau này đổi từ bản giả lập sang gửi
     * email thật mà không phải sửa lớp này.
     */
    public PasswordResetService(NotificationSender sender) {
        this.sender = sender;
    }

    /**
     * Nhận một yêu cầu đặt lại mật khẩu.
     * <p>
     * Không trả về gì và không ném lỗi khi email không tồn tại, tài khoản bị khoá, hay đã xin
     * quá nhiều lần — cả bốn trường hợp phải nhìn giống hệt nhau từ phía người gõ.
     *
     * @param baseUrl địa chỉ gốc của ứng dụng, xem {@link com.fastfood.common.util.WebUtil#baseUrl}
     */
    public void request(String email, String baseUrl, String clientIp) {
        String normalizedEmail;
        try {
            normalizedEmail = ValidationUtil.requireEmail(email);
        } catch (ValidationException e) {
            // Email gõ sai định dạng cũng phải im lặng như email không tồn tại. Báo "email không
            // hợp lệ" thì im lặng trở thành câu trả lời của riêng trường hợp "có thật" — và
            // người dò chỉ cần đọc sự khác nhau đó.
            return;
        }

        Pending pending = Tx.write(con -> {
            User user = userDAO.findByEmail(con, normalizedEmail);
            if (user == null || !user.isActive()) {
                return null;
            }
            LocalDateTime now = DateTimeUtil.now();
            int recent = tokenDAO.countRequestsSince(con, user.getUserId(),
                    now.minusMinutes(requestWindowMinutes()));
            if (recent >= maxRequests()) {
                LOG.warning(() -> "Bo qua yeu cau dat lai mat khau: da xin qua nhieu lan, user_id="
                        + user.getUserId());
                return null;
            }

            // Liên kết cũ hết giá trị ngay khi có liên kết mới: nếu không thì mỗi lần bấm lại
            // là thêm một cánh cửa còn mở, và hộp thư bị lộ một lần là lộ tất cả.
            tokenDAO.invalidateAllFor(con, user.getUserId(), now);

            String rawToken = SecureToken.generate();
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getUserId());
            token.setTokenHash(SecureToken.hash(rawToken));
            token.setExpiresAt(now.plusMinutes(expiryMinutes()));
            token.setRequestedIp(clientIp);
            token.setCreatedAt(now);
            tokenDAO.insert(con, token);

            auditService.log(con, user.getUserId(), "USER", user.getUserId(),
                    AuditAction.PASSWORD_RESET_REQUESTED, clientIp, "SELF_SERVICE");
            return new Pending(user, rawToken);
        });

        // Gửi sau khi giao dịch đã chốt. Gửi ở trong thì hai chuyện hỏng theo hai chiều: kênh
        // gửi tin trục trặc sẽ cuốn theo cả bản ghi mã vừa tạo, còn nếu giao dịch hỏng sau khi
        // tin đã đi thì người dùng cầm một liên kết không tồn tại trong cơ sở dữ liệu.
        if (pending != null) {
            send(pending.user(), baseUrl, pending.rawToken());
        }
    }

    /** Mã vừa cấp, chờ gửi đi sau khi giao dịch chốt. */
    private record Pending(User user, String rawToken) {
    }

    /**
     * Tài khoản mà một mã đang trỏ tới, hoặc {@code null} nếu mã sai, đã dùng, hoặc hết hạn.
     * Dùng để quyết định có dựng biểu mẫu đặt mật khẩu mới ra hay không.
     */
    public User findAccountFor(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        String hash = SecureToken.hash(rawToken);
        return Tx.read(con -> {
            PasswordResetToken token = tokenDAO.findByHash(con, hash);
            if (token == null || !token.isUsable(DateTimeUtil.now())) {
                return null;
            }
            User user = userDAO.findById(con, token.getUserId());
            return user != null && user.isActive() ? user : null;
        });
    }

    /**
     * Đặt mật khẩu mới bằng một mã còn hiệu lực.
     * <p>
     * Kiểm mật khẩu <b>trước</b> khi tiêu mã: gõ mật khẩu mới chưa đủ mạnh là chuyện thường gặp,
     * và tiêu mã ở lần gõ hỏng đầu tiên nghĩa là bắt người dùng quay lại xin liên kết mới chỉ vì
     * họ thiếu một chữ số.
     */
    public void complete(String rawToken, String newPassword, String confirmPassword, String clientIp) {
        ValidationUtil.requirePasswordStrength(newPassword);
        if (!newPassword.equals(confirmPassword)) {
            throw new ValidationException("Mật khẩu nhập lại không khớp.");
        }
        String hash = SecureToken.hash(rawToken == null ? "" : rawToken);

        Tx.writeVoid(con -> {
            LocalDateTime now = DateTimeUtil.now();
            PasswordResetToken token = tokenDAO.findByHash(con, hash);
            if (token == null || !token.isUsable(now)) {
                throw new ValidationException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. "
                        + "Vui lòng yêu cầu lại.");
            }
            User user = userDAO.findById(con, token.getUserId());
            if (user == null || !user.isActive()) {
                throw new ValidationException("Tài khoản không còn hiệu lực. "
                        + "Vui lòng liên hệ quản trị viên.");
            }
            if (!tokenDAO.markUsed(con, token.getTokenId(), now)) {
                // Thua trong cuộc chạy đua với chính mình — người dùng bấm gửi hai lần. Việc đã
                // xong ở lần kia, nhưng không thể báo "thành công" ở đây vì mật khẩu được đặt là
                // của lần kia chứ không phải lần này.
                throw new ValidationException("Liên kết này vừa được dùng rồi. "
                        + "Vui lòng đăng nhập bằng mật khẩu mới.");
            }
            // Tắt luôn cờ buộc đổi mật khẩu: người dùng vừa tự đặt mật khẩu của chính họ, đúng
            // việc mà cờ đó đang chờ.
            userDAO.updatePassword(con, user.getUserId(), PasswordUtil.hash(newPassword), false);
            tokenDAO.invalidateAllFor(con, user.getUserId(), now);
            auditService.log(con, user.getUserId(), "USER", user.getUserId(),
                    AuditAction.PASSWORD_RESET_DONE, clientIp, "SELF_SERVICE");
        });
    }

    /**
     * Vô hiệu hoá mọi liên kết còn treo của một tài khoản, trong giao dịch của nơi gọi.
     * <p>
     * Gọi từ mọi chỗ khác có đổi mật khẩu. Đó là điều người dùng đang ngầm yêu cầu khi họ đổi
     * mật khẩu: đóng lại mọi đường vào cũ, kể cả đường mà chính họ mở ra rồi quên.
     */
    public void invalidateOutstanding(java.sql.Connection con, int userId) throws java.sql.SQLException {
        tokenDAO.invalidateAllFor(con, userId, DateTimeUtil.now());
    }

    // ------------------------------------------------------------------ gửi tin

    private void send(User user, String baseUrl, String rawToken) {
        String link = baseUrl + "/reset-password?token=" + rawToken;
        String content = "Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản " + user.getEmail() + ".\n"
                + "Mở liên kết sau để đặt mật khẩu mới, liên kết có hiệu lực trong "
                + expiryMinutes() + " phút:\n" + link + "\n"
                + "Nếu không phải bạn yêu cầu, hãy bỏ qua thư này — mật khẩu hiện tại vẫn nguyên vẹn.";
        sender.send(user.getEmail(), "Đặt lại mật khẩu", content);
    }

    // ------------------------------------------------------------------ tham số

    public int expiryMinutes() {
        return AppConfig.getInt("security.reset.expiryMinutes", DEFAULT_EXPIRY_MINUTES);
    }

    private int maxRequests() {
        return AppConfig.getInt("security.reset.maxRequests", DEFAULT_MAX_REQUESTS);
    }

    private int requestWindowMinutes() {
        return AppConfig.getInt("security.reset.windowMinutes", DEFAULT_REQUEST_WINDOW_MINUTES);
    }
}
