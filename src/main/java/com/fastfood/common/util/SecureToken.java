package com.fastfood.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Sinh và so sánh chuỗi bí mật dùng một lần: mã chống giả mạo biểu mẫu, mã đặt lại mật khẩu.
 * <p>
 * Ba chi tiết ở đây đều là chỗ dễ làm sai mà hậu quả không nhìn thấy được bằng mắt:
 * <ul>
 *   <li><b>{@link SecureRandom} chứ không phải {@code Math.random()}.</b> Bộ sinh số ngẫu nhiên
 *       thường suy ra được các giá trị tiếp theo từ vài giá trị đã thấy. Mã đặt lại mật khẩu
 *       đoán trước được thì cả luồng quên mật khẩu trở thành cửa sau.</li>
 *   <li><b>So sánh trong thời gian không đổi.</b> {@code String.equals} dừng ngay ở ký tự đầu
 *       tiên khác nhau, nên thời gian trả lời tiết lộ mình đã đoán đúng được mấy ký tự. Đây là
 *       cách bẻ khoá từng ký tự một.</li>
 *   <li><b>Chỉ lưu bản băm xuống cơ sở dữ liệu.</b> Mã đặt lại mật khẩu lưu nguyên văn thì bất kỳ
 *       ai đọc được bảng — bản sao lưu, ảnh chụp màn hình lúc trình bày — đều chiếm được tài khoản
 *       trong thời gian mã còn hạn.</li>
 * </ul>
 */
public final class SecureToken {

    /** 32 byte ngẫu nhiên: quá rộng để dò hết trong khoảng thời gian mã còn hạn. */
    private static final int BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureToken() {
    }

    /** Chuỗi ngẫu nhiên an toàn cho URL — đưa thẳng vào liên kết hoặc ô ẩn của biểu mẫu được. */
    public static String generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Bản băm SHA-256 dạng chữ số mười sáu — thứ duy nhất được phép lưu xuống cơ sở dữ liệu. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 là thuật toán bắt buộc của mọi bản Java; tới đây nghĩa là môi trường hỏng.
            throw new IllegalStateException("Khong tim thay SHA-256", e);
        }
    }

    /**
     * So sánh hai chuỗi bí mật mà thời gian chạy không phụ thuộc vào chúng giống nhau tới đâu.
     * Null coi như không khớp, để nơi gọi không phải kiểm tra trước.
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
