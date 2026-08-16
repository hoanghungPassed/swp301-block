package com.fastfood.common.util;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;

/**
 * Băm và kiểm tra mật khẩu bằng bcrypt.
 * <p>
 * Không bao giờ lưu mật khẩu gốc. Cost 10 là mức cân bằng giữa an toàn và tốc độ đăng nhập.
 * jBCrypt chỉ đọc được tiền tố {@code $2$} và {@code $2a$} — hash sinh bằng htpasswd ra
 * {@code $2y$} phải đổi tiền tố trước khi đưa vào cơ sở dữ liệu.
 */
public final class PasswordUtil {

    private static final int COST = 10;

    /** Độ dài mật khẩu tạm. Dài hơn mức tối thiểu vì nó do máy sinh, không ai phải nhớ nó. */
    private static final int TEMP_LENGTH = 10;

    private PasswordUtil() {
    }

    public static String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(COST));
    }

    /**
     * Mật khẩu tạm để quản trị viên đọc cho người dùng, mỗi lần một khác.
     * <p>
     * Trước đây màn hình quản trị đặt lại mọi tài khoản về cùng một chuỗi cố định. Chuỗi đó thì
     * ai trong lớp cũng biết, nên nó không phải mật khẩu tạm mà là một cánh cửa mở sẵn cho mọi
     * tài khoản vừa được đặt lại. Sinh ngẫu nhiên thì mỗi lần đặt lại chỉ có đúng hai người biết,
     * và cờ {@code must_change_password} thu hẹp nó xuống còn một người ngay lần đăng nhập sau.
     * <p>
     * Bỏ hẳn các ký tự dễ đọc nhầm — số 0 với chữ O, số 1 với chữ l — vì chuỗi này sinh ra để
     * đọc qua điện thoại hoặc chép tay, và một mật khẩu tạm đọc sai là một cuộc gọi nữa.
     * Luôn có cả chữ lẫn số nên chắc chắn qua được {@code ValidationUtil.requirePasswordStrength}.
     */
    public static String randomTemporary() {
        final String letters = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
        final String digits = "23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TEMP_LENGTH);
        // Hai vị trí đầu chốt sẵn một chữ và một số, phần còn lại lấy tự do rồi xáo lại — cách
        // này bảo đảm đủ cả hai loại mà không phải sinh đi sinh lại cho tới khi may mắn.
        sb.append(letters.charAt(random.nextInt(letters.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        String all = letters + digits;
        while (sb.length() < TEMP_LENGTH) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        for (int i = sb.length() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = sb.charAt(i);
            sb.setCharAt(i, sb.charAt(j));
            sb.setCharAt(j, tmp);
        }
        return sb.toString();
    }

    /** Trả về false thay vì ném lỗi khi hash trong DB bị hỏng định dạng. */
    public static boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
