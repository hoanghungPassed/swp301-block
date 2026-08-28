package com.fastfood.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DateTimeUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DISPLAY  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME     = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HTML     = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private DateTimeUtil() {
    }

    /** Trả thời gian hiện tại theo clock của ứng dụng. */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Định dạng ngày giờ để hiển thị. */
    public static String format(LocalDateTime t) {
        return t == null ? "" : t.format(DISPLAY);
    }

    /** Định dạng riêng phần giờ-phút. */
    public static String formatTime(LocalDateTime t) {
        return t == null ? "" : t.format(TIME);
    }

    /** Định dạng riêng phần ngày. */
    public static String formatDate(LocalDateTime t) {
        return t == null ? "" : t.format(DATE);
    }

    /** Chuyển LocalDateTime sang định dạng input datetime-local. */
    public static String toHtmlInput(LocalDateTime t) {
        return t == null ? "" : t.format(HTML);
    }

    /** Parse dữ liệu từ input datetime-local, trả null khi rỗng hoặc sai định dạng. */
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

    /** Tính số phút nguyên giữa hai mốc thời gian. */
    public static long minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(from, to);
    }

    /** Chuyển một mốc thời gian thành câu tương đối như còn 15 phút hoặc muộn 10 phút. */
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
