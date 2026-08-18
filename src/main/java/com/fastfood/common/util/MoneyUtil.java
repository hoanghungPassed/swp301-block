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

    public static String format(BigDecimal amount) {
        return amount == null ? "0 đ" : FORMAT.get().format(amount) + " đ";
    }

    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
