package com.fastfood.common.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Định dạng tiền Việt: 55000 -> "55.000 đ".
 * <p>
 * {@link DecimalFormat} giữ trạng thái nội bộ trong lúc định dạng nên không dùng chung
 * được giữa nhiều luồng: hai người cùng mở một trang có bảng giá thì chuỗi trả về có thể
 * lẫn chữ số của nhau, hoặc ném lỗi ngay giữa lúc dựng trang. Mỗi luồng vì vậy giữ một
 * bản riêng qua {@link ThreadLocal} — vẫn tái sử dụng được, mà không phải tạo mới
 * cho từng lần gọi.
 */
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
