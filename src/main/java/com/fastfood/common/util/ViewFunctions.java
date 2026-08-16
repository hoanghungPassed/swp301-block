package com.fastfood.common.util;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hàm gọi được từ trang JSP, khai báo trong {@code WEB-INF/fastfood.tld}.
 * <p>
 * Giải hai việc mà thẻ JSTL sẵn có không làm được: định dạng {@link LocalDateTime}
 * (thẻ {@code fmt:formatDate} chỉ nhận kiểu Date cũ), và dịch các mã trạng thái sang
 * tiếng Việt ở một chỗ duy nhất thay vì rải câu lệnh điều kiện khắp các trang.
 */
public final class ViewFunctions {

    private ViewFunctions() {
    }

    // ------------------------------------------------------------ định dạng

    public static String dateTime(LocalDateTime value) {
        return DateTimeUtil.format(value);
    }

    public static String time(LocalDateTime value) {
        return DateTimeUtil.formatTime(value);
    }

    public static String date(LocalDateTime value) {
        return DateTimeUtil.formatDate(value);
    }

    public static String money(BigDecimal value) {
        return MoneyUtil.format(value);
    }

    /** "còn 12 phút" hoặc "trễ 5 phút" — dùng ở màn hình bếp và quầy. */
    public static String humanize(LocalDateTime value) {
        return DateTimeUtil.humanize(value);
    }

    // ------------------------------------------------------------ nhãn tiếng Việt

    public static String orderStatus(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case "PENDING_PAYMENT": return "Chờ thanh toán";
            case "CONFIRMED":       return "Đã xác nhận";
            case "PREPARING":       return "Đang chế biến";
            case "READY":           return "Sẵn sàng";
            case "COMPLETED":       return "Đã giao";
            case "CANCELLED":       return "Đã huỷ";
            case "EXPIRED":         return "Hết hạn thanh toán";
            default:                return status;
        }
    }

    /** Tên lớp CSS để tô màu nhãn trạng thái. */
    public static String orderStatusClass(String status) {
        if (status == null) {
            return "tag";
        }
        switch (status) {
            case "PENDING_PAYMENT": return "tag tag-warn";
            case "CONFIRMED":       return "tag tag-info";
            case "PREPARING":       return "tag tag-amber";
            case "READY":           return "tag tag-green";
            case "COMPLETED":       return "tag tag-muted";
            case "CANCELLED":
            case "EXPIRED":         return "tag tag-red";
            default:                return "tag";
        }
    }

    public static String itemStatus(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case "WAITING":   return "Chờ làm";
            case "PREPARING": return "Đang làm";
            case "READY":     return "Xong";
            default:          return status;
        }
    }

    public static String itemStatusClass(String status) {
        if (status == null) {
            return "tag";
        }
        switch (status) {
            case "WAITING":   return "tag tag-muted";
            case "PREPARING": return "tag tag-amber";
            case "READY":     return "tag tag-green";
            default:          return "tag";
        }
    }

    public static String paymentStatus(String status) {
        if (status == null) {
            return "Chưa có";
        }
        switch (status) {
            case "UNPAID":   return "Chưa thu";
            case "PENDING":  return "Đang xử lý";
            case "PAID":     return "Đã thanh toán";
            case "FAILED":   return "Thất bại";
            case "REFUNDED": return "Đã hoàn tiền";
            default:         return status;
        }
    }

    public static String paymentStatusClass(String status) {
        if (status == null) {
            return "tag tag-muted";
        }
        switch (status) {
            case "PAID":     return "tag tag-green";
            case "PENDING":  return "tag tag-warn";
            case "FAILED":   return "tag tag-red";
            case "REFUNDED": return "tag tag-info";
            default:         return "tag tag-muted";
        }
    }

    public static String paymentMethod(String method) {
        if (method == null) {
            return "";
        }
        return "CASH".equals(method) ? "Tiền mặt" : "Thanh toán online";
    }

    public static String orderSource(String source) {
        if (source == null) {
            return "";
        }
        return "POS".equals(source) ? "Tại quầy" : "Đặt trước";
    }

    public static String releaseState(String state) {
        if (state == null) {
            return "";
        }
        switch (state) {
            case "SCHEDULED":       return "Chờ tới giờ vào bếp";
            case "RELEASED_TO_KDS": return "Bếp đã nhận";
            default:                return "Chưa vào bếp";
        }
    }

    public static String issueType(String type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case "OUT_OF_STOCK": return "Hết nguyên liệu";
            case "QUALITY":      return "Chất lượng";
            case "REMAKE":       return "Làm lại";
            default:             return "Khác";
        }
    }

    /**
     * Trạng thái sự cố bếp.
     * <p>
     * Ba màn hình cùng hiện danh sách sự cố (bếp, chi tiết món, thu ngân). Để mỗi trang tự
     * viết điều kiện thì thêm một trạng thái là phải sửa cả ba, và sót một chỗ thì sự cố
     * thu hồi lại hiện thành "đã xử lý" — đúng cái nhầm lẫn mà trạng thái này sinh ra để tránh.
     */
    public static String issueStatus(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case "OPEN":      return "Đang mở";
            case "RESOLVED":  return "Đã xử lý";
            case "CANCELLED": return "Đã thu hồi";
            default:          return status;
        }
    }

    /** Lớp màu của thẻ trạng thái sự cố, đi kèm {@link #issueStatus(String)}. */
    public static String issueStatusTag(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case "OPEN":      return "tag-red";
            case "CANCELLED": return "tag-amber";
            default:          return "tag-green";
        }
    }

    /**
     * Tiêu đề của một tin trong hộp thông báo của khách.
     * <p>
     * Nội dung tin đã nói đủ chuyện gì xảy ra, nhưng nói bằng một đoạn văn. Tiêu đề ngắn ở đây
     * để khách lướt qua danh sách là thấy ngay dòng nào đáng đọc — nhất là dòng báo hoàn tiền
     * nằm lẫn giữa mấy tin xác nhận đơn.
     */
    public static String notificationEvent(String event) {
        if (event == null) {
            return "";
        }
        switch (event) {
            case "ORDER_CONFIRMED": return "Đơn đã được xác nhận";
            case "ORDER_READY":     return "Món đã sẵn sàng";
            case "ORDER_CANCELLED": return "Đơn đã bị huỷ";
            case "ORDER_EXPIRED":   return "Đơn hết hiệu lực";
            default:                return event;
        }
    }

    /** Biểu tượng đi kèm tiêu đề tin, để phân biệt tin vui và tin xấu ngay từ xa. */
    public static String notificationIcon(String event) {
        if (event == null) {
            return "•";
        }
        switch (event) {
            case "ORDER_CONFIRMED": return "✅";
            case "ORDER_READY":     return "🔔";
            case "ORDER_CANCELLED": return "🚫";
            case "ORDER_EXPIRED":   return "⌛";
            default:                return "•";
        }
    }

    /** Lớp màu của thẻ loại tin, đi kèm {@link #notificationEvent(String)}. */
    public static String notificationEventClass(String event) {
        if (event == null) {
            return "tag";
        }
        switch (event) {
            case "ORDER_CONFIRMED": return "tag tag-info";
            case "ORDER_READY":     return "tag tag-green";
            case "ORDER_CANCELLED":
            case "ORDER_EXPIRED":   return "tag tag-red";
            default:                return "tag";
        }
    }

    /** Dịch mã thao tác trong nhật ký sang câu tiếng Việt dễ đọc. */
    public static String auditAction(String action) {
        if (action == null) {
            return "";
        }
        switch (action) {
            case "ORDER_CREATED":        return "Tạo đơn";
            case "PAYMENT_INITIATED":    return "Bắt đầu thanh toán";
            case "PAYMENT_PAID":         return "Thanh toán thành công";
            case "PAYMENT_FAILED":       return "Thanh toán thất bại";
            case "PAYMENT_REFUNDED":     return "Hoàn tiền";
            case "CALLBACK_IGNORED":     return "Bỏ qua kết quả trùng lặp";
            case "AUTO_CONFIRM":         return "Tự động xác nhận đơn";
            case "POS_CONFIRM":          return "Xác nhận đơn tại quầy";
            case "KDS_RELEASE":          return "Đưa đơn xuống bếp";
            case "ITEM_START":           return "Bếp nhận món";
            case "ITEM_READY":           return "Món hoàn thành";
            case "ITEM_HANDED_OVER":     return "Bếp bàn giao ra quầy";
            case "ITEM_RECEIVED":        return "Quầy nhận món";
            case "ISSUE_OPENED":         return "Ghi nhận sự cố";
            case "ISSUE_RESOLVED":       return "Xử lý xong sự cố";
            case "ISSUE_UPDATED":        return "Sửa mô tả sự cố";
            case "ISSUE_CANCELLED":      return "Thu hồi sự cố báo nhầm";
            case "PICKUP_VERIFY_OK":     return "Xác minh mã đúng";
            case "PICKUP_VERIFY_FAILED": return "Mã nhận hàng sai";
            case "HANDOFF":              return "Giao món cho khách";
            case "ORDER_CANCELLED":      return "Huỷ đơn";
            case "ORDER_EXPIRED":        return "Hết hạn thanh toán";
            case "PRODUCT_CHANGED":      return "Thay đổi món ăn";
            case "CATEGORY_CHANGED":     return "Thay đổi nhóm món";
            case "USER_CHANGED":         return "Thay đổi tài khoản";
            default:                     return action;
        }
    }

    public static String roleName(String role) {
        if (role == null) {
            return "";
        }
        switch (role) {
            case "CUSTOMER": return "Khách hàng";
            case "CASHIER":  return "Thu ngân";
            case "KITCHEN":  return "Bếp";
            case "ADMIN":    return "Quản trị";
            default:         return role;
        }
    }
}
