package com.fastfood.common.util;

import com.fastfood.common.exception.AppException.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

public final class ValidationUtil {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");
    private static final Pattern PHONE = Pattern.compile("^0\\d{9,10}$");

    private ValidationUtil() {
    }

    /** Bắt buộc chuỗi có nội dung và trả giá trị đã trim. */
    public static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException("Vui lòng nhập " + fieldName + ".");
        }
        return value.trim();
    }

    /** Bắt buộc email đúng định dạng và chuẩn hóa về chữ thường. */
    public static String requireEmail(String value) {
        String email = requireText(value, "email");
        if (!EMAIL.matcher(email).matches()) {
            throw new ValidationException("Địa chỉ email không hợp lệ.");
        }
        return email.toLowerCase();
    }

    /** Cho phép bỏ trống số điện thoại; nếu có phải bắt đầu bằng 0 và dài 10-11 số. */
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

    public static final int PASSWORD_MIN_LENGTH = 8;

    public static final int PASSWORD_MAX_BYTES = 72;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "12345678", "123456789", "1234567890", "password", "password1", "password123",
            "qwerty123", "abc12345", "iloveyou", "matkhau1", "matkhau123", "admin123",
            "fastfood", "fastfood1", "11111111", "00000000", "1qaz2wsx", "letmein1");

    /** Kiểm tra độ dài BCrypt, chữ-số, khoảng trắng và danh sách mật khẩu phổ biến. */
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

    /** Bắt buộc giá trị số nguyên lớn hơn 0. */
    public static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ValidationException(fieldName + " phải lớn hơn 0.");
        }
        return value;
    }
}
