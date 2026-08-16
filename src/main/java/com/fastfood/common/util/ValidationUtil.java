package com.fastfood.common.util;

import com.fastfood.common.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/** Kiểm tra dữ liệu người dùng nhập. Ném ValidationException với thông báo tiếng Việt. */
public final class ValidationUtil {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");
    private static final Pattern PHONE = Pattern.compile("^0\\d{9,10}$");

    private ValidationUtil() {
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException("Vui lòng nhập " + fieldName + ".");
        }
        return value.trim();
    }

    public static String requireEmail(String value) {
        String email = requireText(value, "email");
        if (!EMAIL.matcher(email).matches()) {
            throw new ValidationException("Địa chỉ email không hợp lệ.");
        }
        return email.toLowerCase();
    }

    public static String optionalPhone(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String phone = value.trim();
        if (!PHONE.matcher(phone).matches()) {
            throw new ValidationException("Số điện thoại phải gồm 10 hoặc 11 chữ số và bắt đầu bằng 0.");
        }
        return phone;
    }

    /** Độ dài tối thiểu. Đủ để một lần dò hết không gian mật khẩu không còn là chuyện làm được. */
    public static final int PASSWORD_MIN_LENGTH = 8;

    /**
     * Giới hạn cứng của bcrypt: thuật toán chỉ đọc 72 byte đầu và <b>bỏ im lặng</b> phần còn lại.
     * Không chặn ở đây thì một mật khẩu dài dòng cẩn thận lại hoá ra chỉ tính tới byte thứ 72,
     * và hai mật khẩu khác nhau từ ký tự thứ 73 trở đi cùng mở được một tài khoản.
     * Đếm theo byte chứ không theo ký tự, vì tiếng Việt có dấu tốn tới ba byte mỗi chữ.
     */
    public static final int PASSWORD_MAX_BYTES = 72;

    /**
     * Những mật khẩu bị thử đầu tiên trong mọi đợt dò, nên chúng vô hiệu hoá mọi quy tắc còn lại:
     * {@code password1} có đủ chữ, đủ số, đủ tám ký tự, và nằm ở đầu mọi danh sách dò.
     * Danh sách ngắn có chủ ý — chặn đúng phần đỉnh, chứ không cố thay thế một bộ từ điển thật.
     */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "12345678", "123456789", "1234567890", "password", "password1", "password123",
            "qwerty123", "abc12345", "iloveyou", "matkhau1", "matkhau123", "admin123",
            "fastfood", "fastfood1", "11111111", "00000000", "1qaz2wsx", "letmein1");

    /**
     * Kiểm tra mật khẩu mới có đủ mạnh không.
     * <p>
     * Chỉ áp dụng lúc <b>đặt</b> mật khẩu, không áp dụng lúc đăng nhập: siết chính sách không
     * được biến thành khoá cửa với những tài khoản đã có từ trước. Người dùng cũ vẫn đăng nhập
     * bình thường và chỉ gặp quy tắc mới khi họ tự đổi mật khẩu.
     */
    public static void requirePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Vui lòng nhập mật khẩu.");
        }
        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new ValidationException(
                    "Mật khẩu phải có ít nhất " + PASSWORD_MIN_LENGTH + " ký tự.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new ValidationException("Mật khẩu quá dài. Vui lòng dùng tối đa "
                    + PASSWORD_MAX_BYTES + " ký tự.");
        }
        if (password.isBlank()) {
            throw new ValidationException("Mật khẩu không được chỉ gồm khoảng trắng.");
        }
        // Khoảng trắng ở hai đầu gần như luôn là do dán nhầm. Không cắt bỏ hộ — cắt hộ thì lần
        // sau người dùng gõ đúng chuỗi họ nhớ lại không vào được — mà báo ra để họ sửa.
        if (!password.equals(password.strip())) {
            throw new ValidationException("Mật khẩu không được bắt đầu hoặc kết thúc bằng khoảng trắng.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            hasLetter |= Character.isLetter(c);
            hasDigit |= Character.isDigit(c);
        }
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("Mật khẩu phải có cả chữ và số.");
        }
        if (COMMON_PASSWORDS.contains(password.toLowerCase())) {
            throw new ValidationException("Mật khẩu này quá phổ biến, vui lòng chọn mật khẩu khác.");
        }
    }

    public static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ValidationException(fieldName + " phải lớn hơn 0.");
        }
        return value;
    }
}
