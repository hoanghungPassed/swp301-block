package com.fastfood.service.auth;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.SecureToken;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.shared.EmailVerificationTokenDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.integration.notification.NotificationSender;
import com.fastfood.integration.notification.NotificationSenders;
import com.fastfood.model.entity.UserEntities.EmailVerificationToken;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmailVerificationService {

    private static final Logger LOG = Logger.getLogger(EmailVerificationService.class.getName());

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

    public EmailVerificationService(NotificationSender sender) {
        this.sender = sender;
    }

    /** Vô hiệu hóa token cũ, sinh token xác thực mới, chỉ lưu hash và trả raw token để gửi thư. */
    public String issue(Connection con, User user, String clientIp) throws SQLException {
        LocalDateTime now = DateTimeUtil.now();

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

    /** Giới hạn số lần yêu cầu, phát token mới trong transaction rồi gửi lại email xác thực. */
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

    /** Ghép raw token vào liên kết /verify-email và gửi nội dung xác thực qua NotificationSender. */
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

    public enum Result {
        VERIFIED,
        ALREADY_VERIFIED,
        INVALID
    }

    /**
     * Hash token nhận từ URL, kiểm tra tồn tại/chưa dùng/chưa hết hạn rồi đánh dấu email verified
     * và vô hiệu hóa các token xác thực khác của user.
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
            if (user.isEmailVerified()) {
                return Result.ALREADY_VERIFIED;
            }
            if (!token.isUsable(now)) {
                return Result.INVALID;
            }
            if (!tokenDAO.markUsed(con, token.getTokenId(), now)
                    || !userDAO.markEmailVerified(con, user.getUserId(), now)) {
                return Result.ALREADY_VERIFIED;
            }
            tokenDAO.invalidateAllFor(con, user.getUserId(), now);
            auditService.log(con, user.getUserId(), "USER", user.getUserId(),
                    AuditAction.EMAIL_VERIFIED, clientIp, user.getEmail());
            return Result.VERIFIED;
        });
    }

    /** Tìm tài khoản sở hữu token để Servlet cập nhật đúng session sau khi xác thực. */
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

    /** Trả thời hạn token xác thực email theo cấu hình, mặc định 24 giờ. */
    public int expiryHours() {
        return AppConfig.getInt("security.verify.expiryHours", DEFAULT_EXPIRY_HOURS);
    }

    /** Trả số lần gửi tối đa trong một cửa sổ giới hạn. */
    private int maxRequests() {
        return AppConfig.getInt("security.verify.maxRequests", DEFAULT_MAX_REQUESTS);
    }

    /** Trả độ dài cửa sổ dùng để đếm số yêu cầu gửi lại. */
    private int requestWindowMinutes() {
        return AppConfig.getInt("security.verify.windowMinutes", DEFAULT_REQUEST_WINDOW_MINUTES);
    }
}
