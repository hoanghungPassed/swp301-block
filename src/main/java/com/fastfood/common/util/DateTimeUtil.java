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

    public static long minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(from, to);
    }

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
