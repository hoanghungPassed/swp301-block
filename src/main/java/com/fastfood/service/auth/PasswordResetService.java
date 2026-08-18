package com.fastfood.service.auth;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.PasswordUtil;
import com.fastfood.common.util.SecureToken;
import com.fastfood.common.util.ValidationUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.shared.PasswordResetTokenDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.integration.notification.NotificationSender;
import com.fastfood.integration.notification.NotificationSenders;
import com.fastfood.model.entity.UserEntities.PasswordResetToken;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.time.LocalDateTime;
import java.util.logging.Logger;

public class PasswordResetService {

    private static final Logger LOG = Logger.getLogger(PasswordResetService.class.getName());

    private static final int DEFAULT_EXPIRY_MINUTES = 15;
    private static final int DEFAULT_MAX_REQUESTS = 3;
    private static final int DEFAULT_REQUEST_WINDOW_MINUTES = 15;

    private final UserDAO userDAO = new UserDAO();
    private final PasswordResetTokenDAO tokenDAO = new PasswordResetTokenDAO();
    private final AuditService auditService = new AuditService();
    private final NotificationSender sender;

    public PasswordResetService() {
        this(NotificationSenders.fromConfig());
    }

    public PasswordResetService(NotificationSender sender) {
        this.sender = sender;
    }

    public void request(String email, String baseUrl, String clientIp) {
        String normalizedEmail;
        try {
            normalizedEmail = ValidationUtil.requireEmail(email);
        } catch (ValidationException e) {
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

        if (pending != null) {
            send(pending.user(), baseUrl, pending.rawToken());
        }
    }

    private record Pending(User user, String rawToken) {
    }

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
                throw new ValidationException("Liên kết này vừa được dùng rồi. "
                        + "Vui lòng đăng nhập bằng mật khẩu mới.");
            }
            userDAO.updatePassword(con, user.getUserId(), PasswordUtil.hash(newPassword), false);
            tokenDAO.invalidateAllFor(con, user.getUserId(), now);
            auditService.log(con, user.getUserId(), "USER", user.getUserId(),
                    AuditAction.PASSWORD_RESET_DONE, clientIp, "SELF_SERVICE");
        });
    }

    public void invalidateOutstanding(java.sql.Connection con, int userId) throws java.sql.SQLException {
        tokenDAO.invalidateAllFor(con, userId, DateTimeUtil.now());
    }

    private void send(User user, String baseUrl, String rawToken) {
        String link = baseUrl + "/reset-password?token=" + rawToken;
        String content = "Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản " + user.getEmail() + ".\n"
                + "Mở liên kết sau để đặt mật khẩu mới, liên kết có hiệu lực trong "
                + expiryMinutes() + " phút:\n" + link + "\n"
                + "Nếu không phải bạn yêu cầu, hãy bỏ qua thư này — mật khẩu hiện tại vẫn nguyên vẹn.";
        sender.send(user.getEmail(), "Đặt lại mật khẩu", content);
    }

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
