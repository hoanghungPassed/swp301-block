package com.fastfood.common.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyUtil {

    private static final DecimalFormatSymbols SYMBOLS;

    static {
        SYMBOLS = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        SYMBOLS.setGroupingSeparator('.');
    }

    private static final ThreadLocal<DecimalFormat> FORMAT =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,###", SYMBOLS));

    private MoneyUtil() {
    }

    /** Định dạng số tiền theo giao diện Việt Nam, ví dụ 98000 thành 98.000 đ. */
    public static String format(BigDecimal amount) {
        return amount == null ? "0 đ" : FORMAT.get().format(amount) + " đ";
    }

    /** Tính thành tiền của một dòng giỏ/đơn mà không làm phát sinh lỗi khi giá null. */
    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
