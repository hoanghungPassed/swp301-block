package com.fastfood.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Nguồn thời gian duy nhất của toàn hệ thống.
 * <p>
 * Mọi mốc thời gian nghiệp vụ đều lấy từ {@link #now()} chứ không dùng {@code SYSDATETIME()}
 * của SQL Server. Lý do: Scheduler so sánh "bây giờ" với giờ dự kiến vào bếp, nếu đồng hồ
 * của Tomcat và SQL Server lệch nhau thì món sẽ được làm sai thời điểm mà rất khó phát hiện.
 * Cố định múi giờ Việt Nam để kết quả không đổi theo cấu hình máy chủ.
 */
public final class DateTimeUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DISPLAY  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME     = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Định dạng của thẻ input type="datetime-local" trên trình duyệt. */
    private static final DateTimeFormatter HTML     = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private DateTimeUtil() {
    }

    /** Thời điểm hiện tại, đã bỏ phần mili giây cho khớp kiểu DATETIME2(0) trong DB. */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS);
    }

    public static String format(LocalDateTime t) {
        return t == null ? "" : t.format(DISPLAY);
    }

    public static String formatTime(LocalDateTime t) {
        return t == null ? "" : t.format(TIME);
    }

    public static String formatDate(LocalDateTime t) {
        return t == null ? "" : t.format(DATE);
    }

    public static String toHtmlInput(LocalDateTime t) {
        return t == null ? "" : t.format(HTML);
    }

    /** Đọc giá trị từ input datetime-local. Trả về null nếu rỗng hoặc sai định dạng. */
    public static LocalDateTime parseHtmlInput(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), HTML).truncatedTo(ChronoUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    /** Số phút từ {@code from} tới {@code to}; âm nghĩa là to đã ở quá khứ so với from. */
    public static long minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(from, to);
    }

    /** Mô tả khoảng cách so với hiện tại, dùng cho màn hình bếp và quầy: "còn 12 phút", "trễ 5 phút". */
    public static String humanize(LocalDateTime target) {
        if (target == null) {
            return "";
        }
        long minutes = minutesBetween(now(), target);
        if (minutes > 0) {
            return "còn " + minutes + " phút";
        }
        if (minutes < 0) {
            return "trễ " + (-minutes) + " phút";
        }
        return "đến giờ";
    }
}
