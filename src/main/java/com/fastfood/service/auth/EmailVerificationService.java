package com.fastfood.service.auth;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.SecureToken;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.shared.EmailVerificationTokenDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.integration.notification.NotificationSender;
import com.fastfood.integration.notification.NotificationSenders;
import com.fastfood.model.entity.EmailVerificationToken;
import com.fastfood.model.entity.User;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Xác thực địa chỉ email của tài khoản khách tự đăng ký.
 * <p>
 * <b>Vì sao cần.</b> Trước đây ô email lúc đăng ký chỉ được kiểm đúng một điều: chuỗi gõ vào
 * có hình dáng của một địa chỉ email. Không có gì buộc địa chỉ ấy tồn tại, và cũng không có gì
 * buộc nó là của người đang gõ. Ba hậu quả, xếp theo mức độ:
 * <ul>
 *   <li><b>Gõ nhầm một chữ.</b> Tài khoản chạy bình thường cho tới ngày người ta quên mật khẩu
 *       — liên kết lấy lại tài khoản gửi tới một hộp thư không ai mở. Lúc đó không còn đường
 *       nào tự cứu, vì email chính là danh tính đăng nhập và không sửa được.</li>
 *   <li><b>Đơn hàng gửi vào hư không.</b> Đơn đặt trước sống bằng email: báo đã xác nhận, báo
 *       món đã sẵn sàng kèm mã nhận hàng. Địa chỉ sai thì khách tới quầy tay không có mã.</li>
 *   <li><b>Chiếm chỗ địa chỉ của người khác.</b> Đăng ký bằng email chưa từng thuộc về mình
 *       thì chủ thật sau này gõ đúng địa chỉ của họ sẽ nhận được "Email này đã được đăng ký".
 *       Cột {@code email} là UNIQUE, nên một lần chiếm là chiếm vĩnh viễn.</li>
 * </ul>
 * <p>
 * <b>Chặn tới đâu.</b> Chưa xác thực vẫn đăng nhập và xem thực đơn được, chỉ không đặt được đơn
 * online — chốt chặn nằm ở {@code CustomerOrderService.createOnlineOrder}. Chặn ngay ở cửa đăng
 * nhập thì thư không tới (rơi vào mục rác, SMTP hỏng, đang ở chế độ giả lập) là người dùng kẹt
 * hoàn toàn, mà lỗi lại không phải của họ. Chặn ở đúng chỗ email thật sự cần thiết thì người
 * chưa xác thực vẫn dùng được phần lớn ứng dụng, và lý do bị chặn hiện ra đúng lúc nó có nghĩa.
 * <p>
 * <b>Các mốc chặn khác</b> giống luồng quên mật khẩu, xem {@link PasswordResetService}: chỉ lưu
 * bản băm, dùng một lần, có hạn, và đếm số lần xin trong ít phút gần đây.
 */
public class EmailVerificationService {

    private static final Logger LOG = Logger.getLogger(EmailVerificationService.class.getName());

    /** Hạn mặc định, tính bằng giờ — xem ghi chú ở bảng trong lược đồ về việc vì sao dài hơn. */
    private static final int DEFAULT_EXPIRY_HOURS = 24;
    private static final int DEFAULT_MAX_REQUESTS = 5;
    private static final int DEFAULT_REQUEST_WINDOW_MINUTES = 15;

    private final UserDAO userDAO = new UserDAO();
    private final EmailVerificationTokenDAO tokenDAO = new EmailVerificationTokenDAO();
    private final AuditService auditService = new AuditService();
    private final NotificationSender sender;

    public EmailVerificationService() {
        this(NotificationSenders.fromConfig());
    }

    /**
     * Nhận kênh gửi tin từ bên ngoài — cùng lý do với {@link PasswordResetService}: mã không lộ
     * ra ở bất kỳ đâu ngoài lá thư, nên bài kiểm thử phải đi đúng đường của người dùng thật.
     */
    public EmailVerificationService(NotificationSender sender) {
        this.sender = sender;
    }

    // ------------------------------------------------------------------ cấp mã

    /**
     * Cấp một mã mới <b>trong giao dịch của nơi gọi</b> và trả về mã gốc để gửi đi sau.
     * <p>
     * Nhận sẵn {@link Connection} chứ không tự mở giao dịch, vì nơi gọi chính là lúc tài khoản
     * vừa được tạo ra và chưa chốt. Tách thành hai giao dịch thì có một khoảnh khắc tài khoản
     * đã tồn tại mà mã thì chưa — và nếu bước sau hỏng, người dùng có một tài khoản không bao
     * giờ nhận được thư, không hiểu vì sao.
     * <p>
     * Không gửi thư ở đây: gửi phải đợi giao dịch chốt xong, nếu không thì thư đi rồi mà giao
     * dịch huỷ, người dùng cầm một liên kết trỏ tới tài khoản không tồn tại.
     *
     * @return mã gốc, chỉ dùng đúng một lần để dựng liên kết rồi bỏ
     */
    public String issue(Connection con, User user, String clientIp) throws SQLException {
        LocalDateTime now = DateTimeUtil.now();

        // Mã cũ hết giá trị ngay khi có mã mới: mỗi lần bấm "gửi lại" mà thêm một liên kết còn
        // sống là thêm một cánh cửa, trong khi người dùng chỉ cần đúng lá thư mới nhất.
        tokenDAO.invalidateAllFor(con, user.getUserId(), now);

        String rawToken = SecureToken.generate();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(user.getUserId());
        token.setTokenHash(SecureToken.hash(rawToken));
        token.setExpiresAt(now.plusHours(expiryHours()));
        token.setRequestedIp(clientIp);
        token.setCreatedAt(now);
        tokenDAO.insert(con, token);

        auditService.log(con, user.getUserId(), "USER", user.getUserId(),
                AuditAction.EMAIL_VERIFY_SENT, clientIp, user.getEmail());
        return rawToken;
    }

    /**
     * Gửi lại thư cho một tài khoản đang đăng nhập mà chưa xác thực.
     * <p>
     * Khác {@link PasswordResetService#request} ở một điểm quan trọng: chỗ này <b>được phép</b>
     * nói thật. Người bấm nút đã đăng nhập, nên họ đã biết tài khoản của chính họ tồn tại và
     * biết địa chỉ của nó — không có thông tin nào để lộ, và im lặng chỉ khiến họ bấm mãi mà
     * không hiểu vì sao thư không tới.
     *
     * @throws BusinessException khi đã xác thực rồi, hoặc vừa xin quá nhiều lần
     */
    public void resend(User user, String baseUrl, String clientIp) {
        if (user.isEmailVerified()) {
            throw new BusinessException("Địa chỉ email của bạn đã được xác thực rồi.");
        }

        String rawToken = Tx.write(con -> {
            LocalDateTime now = DateTimeUtil.now();
            int recent = tokenDAO.countRequestsSince(con, user.getUserId(),
                    now.minusMinutes(requestWindowMinutes()));
            if (recent >= maxRequests()) {
                throw new BusinessException("Bạn đã yêu cầu gửi lại quá nhiều lần. "
                        + "Vui lòng kiểm tra hộp thư (kể cả mục Thư rác) rồi thử lại sau "
                        + requestWindowMinutes() + " phút.");
            }
            return issue(con, user, clientIp);
        });

        send(user, baseUrl, rawToken);
    }

    /**
     * Gửi lá thư chứa liên kết xác thực. Gọi <b>sau khi</b> giao dịch cấp mã đã chốt.
     * <p>
     * Nuốt mọi lỗi của kênh gửi tin: hộp thư hỏng không được kéo đổ việc đăng ký của người dùng,
     * vì tài khoản lúc này đã tạo xong và họ còn nút "gửi lại" để dùng.
     */
    public void send(User user, String baseUrl, String rawToken) {
        try {
            String link = baseUrl + "/verify-email?token=" + rawToken;
            String content = "Chào " + user.getFullName() + ",\n"
                    + "Bạn vừa đăng ký tài khoản Fast Food Pre-order với địa chỉ " + user.getEmail() + ".\n"
                    + "Mở liên kết sau để xác thực địa chỉ này, liên kết có hiệu lực trong "
                    + expiryHours() + " giờ:\n" + link + "\n"
                    + "Xác thực xong bạn mới đặt được đơn online — chúng tôi cần chắc rằng tin báo "
                    + "\"món đã sẵn sàng\" và mã nhận hàng tới đúng hộp thư của bạn.\n"
                    + "Nếu không phải bạn đăng ký, hãy bỏ qua thư này.";
            sender.send(user.getEmail(), "Xác thực địa chỉ email", content);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Khong gui duoc thu xac thuc cho user_id=" + user.getUserId(), e);
        }
    }

    // ------------------------------------------------------------------ xác nhận

    /** Kết quả của một lần bấm liên kết trong thư. */
    public enum Result {
        /** Chính lần bấm này bật được cờ. */
        VERIFIED,
        /** Địa chỉ đã xác thực từ trước — bấm lại lần nữa, hoặc trình duyệt tải trước liên kết. */
        ALREADY_VERIFIED,
        /** Mã sai, hết hạn, hoặc đã bị thay bằng mã mới hơn. */
        INVALID
    }

    /**
     * Xác nhận một địa chỉ bằng mã trong liên kết.
     * <p>
     * Ba kết quả chứ không phải hai, vì "đã xác thực rồi" và "liên kết hỏng" trông giống nhau ở
     * tầng dữ liệu nhưng là hai chuyện hoàn toàn khác với người đọc. Trình duyệt và bộ quét liên
     * kết của các dịch vụ thư đều hay tải trước đường dẫn trong thư, nên lượt bấm thật của người
     * dùng rất thường là lượt thứ hai — báo "liên kết không hợp lệ" ở đúng lượt đó là dựng ra
     * một lỗi không có thật, và người dùng sẽ đi xin thư mới cho một việc đã xong.
     */
    public Result confirm(String rawToken, String clientIp) {
        if (rawToken == null || rawToken.isBlank()) {
            return Result.INVALID;
        }
        String hash = SecureToken.hash(rawToken);

        return Tx.write(con -> {
            LocalDateTime now = DateTimeUtil.now();
            EmailVerificationToken token = tokenDAO.findByHash(con, hash);
            if (token == null) {
                return Result.INVALID;
            }
            User user = userDAO.findById(con, token.getUserId());
            if (user == null) {
                return Result.INVALID;
            }
            // Hỏi cờ trước khi hỏi mã: mã của lá thư trước đã bị đánh dấu dùng ngay lúc xác thực
            // xong, nên nếu xét mã trước thì đúng lá thư mà người dùng vừa bấm thành công lại
            // báo hỏng ở lần bấm thứ hai.
            if (user.isEmailVerified()) {
                return Result.ALREADY_VERIFIED;
            }
            if (!token.isUsable(now)) {
                return Result.INVALID;
            }
            if (!tokenDAO.markUsed(con, token.getTokenId(), now)
                    || !userDAO.markEmailVerified(con, user.getUserId(), now)) {
                // Thua trong cuộc chạy đua với chính mình — hai lượt bấm gần như cùng lúc. Lượt
                // kia đã làm xong việc, nên kết quả đúng vẫn là "đã xác thực".
                return Result.ALREADY_VERIFIED;
            }
            tokenDAO.invalidateAllFor(con, user.getUserId(), now);
            auditService.log(con, user.getUserId(), "USER", user.getUserId(),
                    AuditAction.EMAIL_VERIFIED, clientIp, user.getEmail());
            return Result.VERIFIED;
        });
    }

    /**
     * Tài khoản mà một mã đang trỏ tới, hoặc {@code null} nếu mã không dùng được.
     * <p>
     * Dùng sau khi xác thực xong để làm mới bản chụp người dùng trong phiên: người vừa bấm liên
     * kết thường đang đăng nhập sẵn ở cùng trình duyệt, và nếu không làm mới thì dải nhắc "chưa
     * xác thực email" còn nằm đó tới tận nhịp soi lại tiếp theo — trông như việc vừa làm không
     * ăn thua gì.
     */
    public User findAccountFor(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        String hash = SecureToken.hash(rawToken);
        return Tx.read(con -> {
            EmailVerificationToken token = tokenDAO.findByHash(con, hash);
            return token == null ? null : userDAO.findById(con, token.getUserId());
        });
    }

    // ------------------------------------------------------------------ tham số

    public int expiryHours() {
        return AppConfig.getInt("security.verify.expiryHours", DEFAULT_EXPIRY_HOURS);
    }

    private int maxRequests() {
        return AppConfig.getInt("security.verify.maxRequests", DEFAULT_MAX_REQUESTS);
    }

    private int requestWindowMinutes() {
        return AppConfig.getInt("security.verify.windowMinutes", DEFAULT_REQUEST_WINDOW_MINUTES);
    }
}
