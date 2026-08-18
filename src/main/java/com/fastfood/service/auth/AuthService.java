package com.fastfood.service.auth;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.RoleName;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.PasswordUtil;
import com.fastfood.common.util.ValidationUtil;
import com.fastfood.dao.shared.RoleDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.model.entity.UserEntities.Role;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.time.Duration;

public class AuthService {

    private final UserDAO userDAO = new UserDAO();
    private final RoleDAO roleDAO = new RoleDAO();
    private final AuditService auditService = new AuditService();
    private final LoginThrottle throttle = LoginThrottle.getInstance();
    private final PasswordResetService passwordResetService = new PasswordResetService();
    private final EmailVerificationService verificationService = new EmailVerificationService();

    public User login(String email, String rawPassword) {
        String normalizedEmail = ValidationUtil.requireEmail(email);
        ValidationUtil.requireText(rawPassword, "mật khẩu");

        User user = Tx.read(con -> userDAO.findByEmail(con, normalizedEmail));
        if (user == null || !PasswordUtil.matches(rawPassword, user.getPasswordHash())) {
            throw new ValidationException("Email hoặc mật khẩu không đúng.");
        }
        if (!user.isActive()) {
            throw new BusinessException("Tài khoản đã bị khoá. Vui lòng liên hệ quản trị viên.");
        }
        return user;
    }

    public User login(String email, String rawPassword, String clientIp) {
        String normalizedEmail = ValidationUtil.requireEmail(email);
        ValidationUtil.requireText(rawPassword, "mật khẩu");

        Duration remaining = throttle.lockRemaining(normalizedEmail, clientIp);
        if (remaining != null) {
            throw new BusinessException("Bạn đã nhập sai quá nhiều lần. "
                    + "Vui lòng thử lại sau " + minutesLeft(remaining) + " phút.");
        }

        User user = Tx.read(con -> userDAO.findByEmail(con, normalizedEmail));
        boolean ok = user != null && PasswordUtil.matches(rawPassword, user.getPasswordHash());
        if (!ok) {
            onFailedLogin(normalizedEmail, clientIp, user);
            throw new ValidationException("Email hoặc mật khẩu không đúng.");
        }

        throttle.recordSuccess(normalizedEmail, clientIp);
        if (!user.isActive()) {
            throw new BusinessException("Tài khoản đã bị khoá. Vui lòng liên hệ quản trị viên.");
        }
        auditService.logRejected(user.getUserId(), "USER", user.getUserId(),
                AuditAction.LOGIN_SUCCESS, null, clientIp);
        return user;
    }

    private void onFailedLogin(String normalizedEmail, String clientIp, User user) {
        Integer actorId = user == null ? null : user.getUserId();
        Object entityId = user == null ? 0 : user.getUserId();
        boolean justLocked = throttle.recordFailure(normalizedEmail, clientIp);
        auditService.logRejected(actorId, "USER", entityId,
                AuditAction.LOGIN_FAILED, clientIp, normalizedEmail);
        if (justLocked) {
            auditService.logRejected(actorId, "USER", entityId,
                    AuditAction.LOGIN_BLOCKED, clientIp, normalizedEmail);
        }
    }

    public void logout(int userId, String clientIp) {
        auditService.logRejected(userId, "USER", userId, AuditAction.LOGOUT, null, clientIp);
    }

    private long minutesLeft(Duration remaining) {
        return Math.max(1, (remaining.toSeconds() + 59) / 60);
    }

    public User register(String fullName, String email, String phone, String password, String confirmPassword) {
        return register(fullName, email, phone, password, confirmPassword, null, null);
    }

    public User register(String fullName, String email, String phone, String password,
                         String confirmPassword, String baseUrl, String clientIp) {
        String name = ValidationUtil.requireText(fullName, "họ tên");
        String normalizedEmail = ValidationUtil.requireEmail(email);
        String normalizedPhone = ValidationUtil.optionalPhone(phone);
        ValidationUtil.requirePasswordStrength(password);
        if (!password.equals(confirmPassword)) {
            throw new ValidationException("Mật khẩu nhập lại không khớp.");
        }

        Registered registered = Tx.write(con -> {
            if (userDAO.emailExists(con, normalizedEmail)) {
                throw new ValidationException("Email này đã được đăng ký.");
            }
            Role role = roleDAO.findByName(con, RoleName.CUSTOMER.name());
            User u = new User();
            u.setFullName(name);
            u.setEmail(normalizedEmail);
            u.setPhone(normalizedPhone);
            u.setPasswordHash(PasswordUtil.hash(password));
            u.setRoleId(role.getRoleId());
            u.setStatus("ACTIVE");
            u.setEmailVerified(false);
            u.setCreatedAt(DateTimeUtil.now());
            userDAO.insert(con, u);
            u.setRoleName(role.getName());
            auditService.log(con, u.getUserId(), "USER", u.getUserId(),
                    AuditAction.USER_CHANGED, clientIp, "REGISTERED");
            String rawToken = baseUrl == null ? null : verificationService.issue(con, u, clientIp);
            return new Registered(u, rawToken);
        });

        if (registered.rawToken() != null) {
            verificationService.send(registered.user(), baseUrl, registered.rawToken());
        }
        return registered.user();
    }

    private record Registered(User user, String rawToken) {
    }

    public User findById(int userId) {
        return Tx.read(con -> userDAO.findById(con, userId));
    }

    public void updateProfile(int userId, String fullName, String phone) {
        String name = ValidationUtil.requireText(fullName, "họ tên");
        String normalizedPhone = ValidationUtil.optionalPhone(phone);
        Tx.writeVoid(con -> {
            User u = userDAO.findById(con, userId);
            if (u == null) {
                throw new ValidationException("Không tìm thấy tài khoản.");
            }
            u.setFullName(name);
            u.setPhone(normalizedPhone);
            u.setUpdatedAt(DateTimeUtil.now());
            userDAO.updateProfile(con, u);
        });
    }

    public void changePassword(int userId, String currentPassword, String newPassword, String confirmPassword) {
        ValidationUtil.requirePasswordStrength(newPassword);
        if (!newPassword.equals(confirmPassword)) {
            throw new ValidationException("Mật khẩu nhập lại không khớp.");
        }
        Tx.writeVoid(con -> {
            User u = userDAO.findById(con, userId);
            if (u == null || !PasswordUtil.matches(currentPassword, u.getPasswordHash())) {
                throw new ValidationException("Mật khẩu hiện tại không đúng.");
            }
            userDAO.updatePassword(con, userId, PasswordUtil.hash(newPassword), false);
            passwordResetService.invalidateOutstanding(con, userId);
            auditService.log(con, userId, "USER", userId, AuditAction.USER_CHANGED, null, "PASSWORD_CHANGED");
        });
    }
}
