package com.fastfood.common.util;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;

/**
 * Sinh mã nhận hàng cho đơn đặt trước.
 * <p>
 * Định dạng {@code yyMMdd} + 4 ký tự ngẫu nhiên, ví dụ {@code 260813A1C7}. Phần ngày ở đầu
 * khiến mã của các ngày khác nhau không bao giờ đụng nhau, nên chỉ cần chống trùng trong
 * phạm vi một ngày. Bảng chữ cái bỏ các ký tự dễ đọc nhầm (0/O, 1/I) vì nhân viên phải
 * gõ tay mã này khi khách không quét được QR.
 */
public final class PickupCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private PickupCodeGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(DateTimeUtil.now().format(DAY));
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
