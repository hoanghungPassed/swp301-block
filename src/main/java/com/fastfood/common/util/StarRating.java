package com.fastfood.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class StarRating {

    public static final int MAX = 5;

    private static final char DAC = '★';
    private static final char RONG = '☆';

    private StarRating() {
    }

    /** Chuyển điểm nguyên 0..5 thành chuỗi năm sao đặc/rỗng để JSP hiển thị. */
    public static String of(int rating) {
        StringBuilder sb = new StringBuilder(MAX);
        for (int i = 1; i <= MAX; i++) {
            sb.append(i <= rating ? DAC : RONG);
        }
        return sb.toString();
    }

    /** Làm tròn điểm trung bình rồi dùng cùng quy tắc dựng chuỗi sao. */
    public static String of(BigDecimal average) {
        if (average == null) {
            return of(0);
        }
        return of(average.setScale(0, RoundingMode.HALF_UP).intValue());
    }
}
