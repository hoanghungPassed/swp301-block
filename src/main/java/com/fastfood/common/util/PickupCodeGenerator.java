package com.fastfood.common.util;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;

public final class PickupCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private PickupCodeGenerator() {
    }

    /** Sinh mã nhận hàng ngẫu nhiên theo độ dài cấu hình, loại bỏ ký tự dễ đọc nhầm. */
    public static String generate() {
        StringBuilder sb = new StringBuilder(DateTimeUtil.now().format(DAY));
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
