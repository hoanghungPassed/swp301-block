package com.fastfood.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Vẽ điểm đánh giá thành chuỗi sao.
 * <p>
 * Gom về một chỗ vì ba nơi cùng cần: một đánh giá lẻ, bản tóm tắt của một món, và thẻ món trên
 * lưới thực đơn. Chép ba lần thì đổi cách hiển thị phải nhớ đủ ba chỗ, và chỗ nào quên thì lệch
 * so với hai chỗ kia ngay trên cùng một trang.
 * <p>
 * Dùng ký tự chứ không dùng ảnh: không tải thêm gì, và phóng to chữ thì sao phóng theo. Cố ý
 * không có nửa sao — một chuỗi năm ký tự đọc được bằng mắt lướt qua, còn con số chính xác luôn
 * đứng ngay bên cạnh cho ai muốn biết kỹ.
 */
public final class StarRating {

    public static final int MAX = 5;

    private static final char DAC = '★';
    private static final char RONG = '☆';

    private StarRating() {
    }

    /** Chuỗi sao cho một điểm nguyên, ví dụ {@code 3 → ★★★☆☆}. */
    public static String of(int rating) {
        StringBuilder sb = new StringBuilder(MAX);
        for (int i = 1; i <= MAX; i++) {
            sb.append(i <= rating ? DAC : RONG);
        }
        return sb.toString();
    }

    /** Chuỗi sao cho điểm trung bình, làm tròn tới sao gần nhất. Rỗng thì không sao nào đặc. */
    public static String of(BigDecimal average) {
        if (average == null) {
            return of(0);
        }
        return of(average.setScale(0, RoundingMode.HALF_UP).intValue());
    }
}
