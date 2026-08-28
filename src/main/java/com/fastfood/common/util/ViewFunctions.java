package com.fastfood.common.util;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ViewFunctions {

    private ViewFunctions() {
    }

    /** Định dạng ngày giờ cho EL/JSP. */
    public static String dateTime(LocalDateTime value) {
        return DateTimeUtil.format(value);
    }

    /** Định dạng riêng giờ phút cho màn theo dõi đơn. */
    public static String time(LocalDateTime value) {
        return DateTimeUtil.formatTime(value);
    }

    /** Định dạng riêng ngày cho giao diện. */
    public static String date(LocalDateTime value) {
        return DateTimeUtil.formatDate(value);
    }

    /** Định dạng tiền Việt Nam cho JSP. */
    public static String money(BigDecimal value) {
        return MoneyUtil.format(value);
    }

    /** Chuyển thời điểm thành mô tả tương đối như “5 phút trước”. */
    public static String humanize(LocalDateTime value) {
        return DateTimeUtil.humanize(value);
    }

    /** Dịch mã trạng thái đơn trong DB sang nhãn tiếng Việt. */
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
            case "EXPIRED":         return "Hết hạn thanh toán";
            default:                return status;
        }
    }

    /** Chọn lớp CSS tương ứng với trạng thái đơn. */
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
            case "EXPIRED":         return "tag tag-red";
            default:                return "tag";
        }
    }

    /** Dịch trạng thái từng món sang nhãn hiển thị. */
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

    /** Chọn màu tag cho trạng thái món. */
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

    /** Dịch trạng thái thanh toán cho Customer. */
    public static String paymentStatus(String status) {
        if (status == null) {
            return "Chưa có";
        }
        switch (status) {
            case "UNPAID":   return "Chưa thu";
            case "PENDING":  return "Đang xử lý";
            case "PAID":     return "Đã thanh toán";
            case "FAILED":   return "Thất bại";
            default:         return status;
        }
    }

    /** Chọn màu tag theo kết quả thanh toán. */
    public static String paymentStatusClass(String status) {
        if (status == null) {
            return "tag tag-muted";
        }
        switch (status) {
            case "PAID":     return "tag tag-green";
            case "PENDING":  return "tag tag-warn";
            case "FAILED":   return "tag tag-red";
            default:         return "tag tag-muted";
        }
    }

    /** Dịch phương thức CASH/ONLINE thành nhãn dễ đọc. */
    public static String paymentMethod(String method) {
        if (method == null) {
            return "";
        }
        return "CASH".equals(method) ? "Tiền mặt" : "Thanh toán online";
    }

    /** Phân biệt đơn tại quầy và đơn đặt trước. */
    public static String orderSource(String source) {
        if (source == null) {
            return "";
        }
        return "POS".equals(source) ? "Tại quầy" : "Đặt trước";
    }

    /** Dịch trạng thái hẹn giờ đưa đơn vào bếp. */
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

    /** Dịch loại thông báo đơn cho Customer. */
    public static String notificationEvent(String event) {
        if (event == null) {
            return "";
        }
        switch (event) {
            case "ORDER_CONFIRMED": return "Đơn đã được xác nhận";
            case "ORDER_READY":     return "Món đã sẵn sàng";
            case "ORDER_EXPIRED":   return "Đơn hết hiệu lực";
            default:                return event;
        }
    }

    /** Chọn biểu tượng cho từng loại thông báo. */
    public static String notificationIcon(String event) {
        if (event == null) {
            return "•";
        }
        switch (event) {
            case "ORDER_CONFIRMED": return "✅";
            case "ORDER_READY":     return "🔔";
            case "ORDER_EXPIRED":   return "⌛";
            default:                return "•";
        }
    }

    /** Chọn màu tag cho loại thông báo. */
    public static String notificationEventClass(String event) {
        if (event == null) {
            return "tag";
        }
        switch (event) {
            case "ORDER_CONFIRMED": return "tag tag-info";
            case "ORDER_READY":     return "tag tag-green";
            case "ORDER_EXPIRED":   return "tag tag-red";
            default:                return "tag";
        }
    }

    /** Dịch mã action trong audit log thành mô tả nghiệp vụ. */
    public static String auditAction(String action) {
        if (action == null) {
            return "";
        }
        switch (action) {
            case "ORDER_CREATED":        return "Tạo đơn";
            case "PAYMENT_INITIATED":    return "Bắt đầu thanh toán";
            case "PAYMENT_PAID":         return "Thanh toán thành công";
            case "PAYMENT_FAILED":       return "Thanh toán thất bại";
            case "PAYMENT_ORPHANED":     return "Tiền về sau khi đơn hết hiệu lực";
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
            case "ORDER_EXPIRED":        return "Hết hạn thanh toán";
            case "PRODUCT_CHANGED":      return "Thay đổi món ăn";
            case "CATEGORY_CHANGED":     return "Thay đổi nhóm món";
            case "USER_CHANGED":         return "Thay đổi tài khoản";
            default:                     return action;
        }
    }

    /**
     * Chuỗi truy vấn hiện tại sau khi bỏ vài tham số, để thẻ phân trang tự nối
     * số trang mới mà vẫn giữ nguyên bộ lọc người dùng đang xem.
     *
     * @param omit danh sách tên tham số cần bỏ, ngăn nhau bằng dấu phẩy
     * @return chuỗi đã mã hoá URL, không có dấu ? ở đầu; rỗng nếu không còn tham số nào
     */
    public static String pageQuery(HttpServletRequest req, String omit) {
        if (req == null) {
            return "";
        }
        String[] names = omit == null ? new String[0] : omit.split(",");
        for (int i = 0; i < names.length; i++) {
            names[i] = names[i].trim();
        }
        return WebUtil.queryStringWithout(req, names);
    }

    /** Dịch mã role để header và hồ sơ hiển thị thân thiện. */
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
