package com.fastfood.common.constant;

/**
 * Loại sự cố bếp — khớp với ràng buộc {@code CK_Issue_type} của bảng KitchenIssue.
 * <p>
 * Có kiểu liệt kê ở đây để loại sự cố gửi lên được kiểm tra tại tầng nghiệp vụ. Thiếu nó thì
 * một giá trị lạ đi thẳng xuống cơ sở dữ liệu và vỡ ở ràng buộc CHECK — người dùng nhận về
 * lỗi hệ thống thay vì một thông báo đọc được.
 */
public enum IssueType {

    /** Hết nguyên liệu. Kéo theo việc tắt món trên thực đơn — xem KitchenService.openIssue. */
    OUT_OF_STOCK,

    /** Món không đạt chất lượng. */
    QUALITY,

    /** Phải làm lại từ đầu. */
    REMAKE,

    /** Thu ngân từ chối nhận món bếp đưa ra quầy: sai món, nguội, thiếu phần. */
    COUNTER_REJECT,

    OTHER;

    public static IssueType from(String value) {
        for (IssueType t : values()) {
            if (t.name().equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown issue type: " + value);
    }
}
