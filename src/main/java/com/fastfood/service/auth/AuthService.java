package com.fastfood.service.auth;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.constant.RoleName;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.PasswordUtil;
import com.fastfood.common.util.ValidationUtil;
import com.fastfood.dao.shared.RoleDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.model.entity.Role;
import com.fastfood.model.entity.User;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.time.Duration;

/** Đăng nhập, đăng ký và đổi thông tin tài khoản. */
public class AuthService {

    private final UserDAO userDAO = new UserDAO();
    private final RoleDAO roleDAO = new RoleDAO();
    private final AuditService auditService = new AuditService();
    private final LoginThrottle throttle = LoginThrottle.getInstance();
    private final PasswordResetService passwordResetService = new PasswordResetService();
    private final EmailVerificationService verificationService = new EmailVerificationService();

    /**
     * Kiểm tra thông tin đăng nhập, không đếm số lần thử sai.
     * <p>
     * Sai email và sai mật khẩu trả về cùng một thông báo, để người ngoài không dò được
     * email nào đã có trong hệ thống.
     * <p>
     * Dùng khi không có ngữ cảnh web — kiểm thử, hoặc mã gọi từ máy chủ. Đường đi của người
     * dùng thật là {@link #login(String, String, String)}.
     */
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

    /**
     * Đăng nhập kèm đếm số lần thử sai và ghi nhật ký.
     * <p>
     * Cửa bị khoá thì <b>không</b> đối chiếu mật khẩu nữa — kiểm tra trước cả khi băm. Băm rồi mới
     * từ chối thì mỗi lần thử vẫn tốn đúng chừng ấy công của máy chủ, nghĩa là cơ chế khoá chặn
     * được việc đoán mật khẩu nhưng không chặn được việc làm nghẽn máy chủ.
     * <p>
     * Thông báo khi bị khoá nói thẳng là còn bao nhiêu phút. Giấu đi thì người gõ nhầm mật khẩu
     * của chính mình cứ thử lại mãi và tin rằng tài khoản đã hỏng; còn với người đang dò thì con
     * số đó chẳng cho họ thêm điều gì — họ tự biết mình vừa thử bao nhiêu lần.
     *
     * @param clientIp địa chỉ máy khách, xem {@link com.fastfood.common.util.WebUtil#clientIp}
     */
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

        // Đếm về không kể cả khi tài khoản bị khoá: mật khẩu đã đúng nên đây không phải người
        // đang dò, và họ sẽ còn quay lại sau khi được mở khoá.
        throttle.recordSuccess(normalizedEmail, clientIp);
        if (!user.isActive()) {
            throw new BusinessException("Tài khoản đã bị khoá. Vui lòng liên hệ quản trị viên.");
        }
        auditService.logRejected(user.getUserId(), "USER", user.getUserId(),
                AuditAction.LOGIN_SUCCESS, null, clientIp);
        return user;
    }

    /**
     * Ghi lại một lần đăng nhập hỏng.
     * <p>
     * Nhật ký ghi bằng giao dịch riêng ({@code logRejected}) vì việc đang mô tả là một việc
     * <i>bị từ chối</i> — không có giao dịch nào khác để ghi kèm vào, và nếu có thì nó cũng sắp
     * bị huỷ, mang theo cả dòng nhật ký đáng chú ý nhất.
     */
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

    /** Đăng xuất. Ghi nhật ký để lịch sử một tài khoản có cả hai đầu, không chỉ lúc vào. */
    public void logout(int userId, String clientIp) {
        auditService.logRejected(userId, "USER", userId, AuditAction.LOGOUT, null, clientIp);
    }

    /** Làm tròn lên: còn 10 giây nữa mà báo "0 phút" thì người đọc tưởng đã hết khoá. */
    private long minutesLeft(Duration remaining) {
        return Math.max(1, (remaining.toSeconds() + 59) / 60);
    }

    /**
     * Đăng ký tài khoản khách hàng, không gửi thư xác thực. Nhân viên do quản trị viên tạo,
     * không tự đăng ký.
     * <p>
     * Dùng khi không có ngữ cảnh web — kiểm thử, hoặc mã gọi từ máy chủ. Đường đi của người
     * dùng thật là {@link #register(String, String, String, String, String, String, String)}:
     * thiếu địa chỉ gốc của ứng dụng thì không dựng được liên kết trong thư, nên bản này để
     * tài khoản ở trạng thái chưa xác thực và không có mã nào được cấp.
     */
    public User register(String fullName, String email, String phone, String password, String confirmPassword) {
        return register(fullName, email, phone, password, confirmPassword, null, null);
    }

    /**
     * Đăng ký tài khoản khách hàng rồi gửi thư xác thực địa chỉ email.
     * <p>
     * Tài khoản sinh ra ở trạng thái <b>chưa xác thực</b>: người đăng ký mới chỉ gõ ra một địa
     * chỉ chứ chưa chứng minh mình mở được hộp thư đó. Xem {@link EmailVerificationService} để
     * biết vì sao điều đó quan trọng và bị chặn tới đâu.
     * <p>
     * Mã xác thực được cấp <b>trong cùng giao dịch</b> tạo tài khoản, còn thư gửi sau khi giao
     * dịch chốt — cùng thế cân bằng như ở luồng quên mật khẩu: gửi trong giao dịch thì kênh gửi
     * tin trục trặc sẽ cuốn theo cả tài khoản vừa tạo, mà việc đăng ký thì đã hoàn tất đúng đắn.
     *
     * @param baseUrl địa chỉ gốc của ứng dụng, xem {@link com.fastfood.common.util.WebUtil#baseUrl}
     */
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

    /** Tài khoản vừa tạo cùng mã xác thực chờ gửi đi sau khi giao dịch chốt. */
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
            // Tự đổi thì gỡ luôn cờ bắt buộc đổi — đây chính là việc mà cờ đó đang chờ.
            userDAO.updatePassword(con, userId, PasswordUtil.hash(newPassword), false);
            // Đổi mật khẩu là lúc người dùng muốn đóng mọi đường vào cũ. Một liên kết quên mật
            // khẩu xin từ trước mà vẫn dùng được sau đó thì đúng cánh cửa họ vừa khoá lại là
            // cánh cửa còn mở.
            passwordResetService.invalidateOutstanding(con, userId);
            auditService.log(con, userId, "USER", userId, AuditAction.USER_CHANGED, null, "PASSWORD_CHANGED");
        });
    }
}
