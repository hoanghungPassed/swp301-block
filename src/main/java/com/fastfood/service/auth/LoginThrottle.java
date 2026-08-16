package com.fastfood.service.auth;

import com.fastfood.config.AppConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Đếm số lần đăng nhập sai và tạm khoá cửa khi vượt ngưỡng.
 * <p>
 * <b>Vì sao cần.</b> Bcrypt làm mỗi lần thử tốn khoảng một phần mười giây, nghe thì chậm nhưng
 * để nguyên như vậy vẫn là vài trăm nghìn lần thử mỗi ngày trên một tài khoản — thừa sức quét
 * hết một danh sách mật khẩu thông dụng. Chính sách mật khẩu mạnh chặn được những mật khẩu tệ
 * nhất, còn thứ chặn <i>tốc độ dò</i> thì phải là cơ chế này.
 * <p>
 * <b>Khoá theo cặp email + địa chỉ máy, không khoá theo mình email.</b> Khoá theo email thôi thì
 * bất kỳ ai cũng vô hiệu hoá được tài khoản của người khác chỉ bằng cách gõ sai mật khẩu năm lần
 * — đó là biến một cơ chế bảo vệ thành một cơ chế phá hoại. Kèm địa chỉ máy thì hậu quả của
 * việc gõ sai chỉ rơi vào chính máy đang gõ.
 * <p>
 * <b>Vì sao để trong bộ nhớ chứ không xuống cơ sở dữ liệu.</b> Dữ liệu này hết giá trị sau vài
 * phút và không ai cần tra lại nó — thứ cần tra lại là dòng nhật ký {@code LOGIN_FAILED}, và
 * dòng đó vẫn được ghi xuống bảng {@code AuditLog} như thường. Khởi động lại máy chủ thì bộ đếm
 * về không; đó là đánh đổi chấp nhận được cho một hệ thống chạy trên một máy chủ duy nhất, và
 * nó cũng có nghĩa là không có bảng nào phình ra vì những lần gõ nhầm.
 */
public class LoginThrottle {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_LOCK_MINUTES = 15;
    /** Gõ sai một lần rồi thôi thì sau khoảng này coi như chưa có gì xảy ra. */
    private static final int DEFAULT_WINDOW_MINUTES = 15;

    /**
     * Dùng chung cho cả ứng dụng. Mỗi servlet tự tạo một {@code AuthService} riêng, mà bộ đếm
     * thì phải là một: hai bản đếm riêng nghĩa là mỗi bản chỉ thấy một nửa số lần thử, và ngưỡng
     * năm lần trên thực tế thành mười.
     */
    private static final LoginThrottle INSTANCE = new LoginThrottle();

    public static LoginThrottle getInstance() {
        return INSTANCE;
    }

    private final Map<String, Attempts> byKey = new ConcurrentHashMap<>();

    LoginThrottle() {
    }

    /** Còn bao lâu nữa mới thử lại được, hoặc {@code null} nếu đang không bị khoá. */
    public Duration lockRemaining(String email, String clientIp) {
        Attempts a = byKey.get(key(email, clientIp));
        if (a == null || a.lockedUntil == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        return now.isBefore(a.lockedUntil) ? Duration.between(now, a.lockedUntil) : null;
    }

    public boolean isLocked(String email, String clientIp) {
        return lockRemaining(email, clientIp) != null;
    }

    /**
     * Ghi nhận một lần sai.
     *
     * @return true nếu chính lần này làm cửa bị khoá lại — nơi gọi dùng để ghi nhật ký đúng một
     *         lần cho mỗi đợt, thay vì ghi lại ở mọi lần thử tiếp theo
     */
    public boolean recordFailure(String email, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        Attempts updated = byKey.compute(key(email, clientIp), (k, current) -> {
            Attempts a = (current == null || current.isStale(now)) ? new Attempts() : current;
            a.count++;
            a.lastFailureAt = now;
            if (a.count >= maxAttempts() && a.lockedUntil == null) {
                a.lockedUntil = now.plusMinutes(lockMinutes());
                a.justLocked = true;
            } else {
                a.justLocked = false;
            }
            return a;
        });
        purgeStale(now);
        return updated.justLocked;
    }

    /** Đăng nhập được rồi thì xoá sạch lịch sử của cặp email + máy đó. */
    public void recordSuccess(String email, String clientIp) {
        byKey.remove(key(email, clientIp));
    }

    /** Chỉ dùng trong kiểm thử: trả bộ đếm về trạng thái trắng. */
    void clear() {
        byKey.clear();
    }

    // ------------------------------------------------------------------ nội bộ

    private String key(String email, String clientIp) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        return normalizedEmail + "|" + (clientIp == null ? "" : clientIp);
    }

    /**
     * Dọn những mục đã hết hạn. Không có bước này thì bản đồ chỉ lớn lên chứ không nhỏ lại, và
     * một đợt dò trên hàng nghìn email để lại hàng nghìn mục nằm trong bộ nhớ vĩnh viễn.
     */
    private void purgeStale(LocalDateTime now) {
        if (byKey.size() < 1000) {
            return;
        }
        byKey.values().removeIf(a -> a.isStale(now));
    }

    private int maxAttempts() {
        return AppConfig.getInt("security.login.maxAttempts", DEFAULT_MAX_ATTEMPTS);
    }

    private int lockMinutes() {
        return AppConfig.getInt("security.login.lockMinutes", DEFAULT_LOCK_MINUTES);
    }

    private int windowMinutes() {
        return AppConfig.getInt("security.login.windowMinutes", DEFAULT_WINDOW_MINUTES);
    }

    private final class Attempts {
        private int count;
        private LocalDateTime lastFailureAt;
        private LocalDateTime lockedUntil;
        private boolean justLocked;

        /** Hết khoá và im ắng đủ lâu thì mục này không còn nói lên điều gì nữa. */
        private boolean isStale(LocalDateTime now) {
            if (lockedUntil != null && now.isBefore(lockedUntil)) {
                return false;
            }
            return lastFailureAt == null || lastFailureAt.plusMinutes(windowMinutes()).isBefore(now);
        }
    }
}
