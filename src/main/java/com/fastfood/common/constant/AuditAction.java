package com.fastfood.common.constant;

/**
 * Audit action - NFR-08: payment success, auto-confirm, KDS release, item start/ready,
 * pickup verification, handoff, cancel/refund và admin action đều phải có audit event.
 */
public final class AuditAction {

    // Payment
    public static final String PAYMENT_INITIATED = "PAYMENT_INITIATED";
    public static final String PAYMENT_PAID      = "PAYMENT_PAID";
    public static final String PAYMENT_FAILED    = "PAYMENT_FAILED";
    public static final String PAYMENT_REFUNDED  = "PAYMENT_REFUNDED";
    public static final String CALLBACK_IGNORED  = "CALLBACK_IGNORED";   // duplicate external_transaction_id

    // Order lifecycle
    public static final String ORDER_CREATED     = "ORDER_CREATED";
    public static final String AUTO_CONFIRM      = "AUTO_CONFIRM";       // BR-07
    public static final String POS_CONFIRM       = "POS_CONFIRM";        // BR-10
    public static final String ORDER_EXPIRED     = "ORDER_EXPIRED";      // BR-13
    public static final String ORDER_CANCELLED   = "ORDER_CANCELLED";    // BR-12
    public static final String ORDER_COMPLETED   = "ORDER_COMPLETED";    // BR-16

    // Kitchen / KDS
    public static final String KDS_RELEASE       = "KDS_RELEASE";        // BR-09, ghi 1 lần
    /**
     * Nhận việc và bắt đầu làm là một thao tác, không phải hai: trạng thái món chỉ có
     * WAITING -> PREPARING -> READY, không có bậc trung gian "đã nhận nhưng chưa làm".
     * Vì vậy không có hằng số ITEM_CLAIM riêng.
     */
    public static final String ITEM_START        = "ITEM_START";
    public static final String ITEM_READY        = "ITEM_READY";
    /**
     * Bàn giao món từ bếp ra quầy, và quầy xác nhận đã cầm. Hai mã riêng vì hai người khác
     * nhau thực hiện; gộp một mã thì nhật ký không còn trả lời được câu hỏi hay gặp nhất khi
     * mất món: bếp đã đưa ra chưa, hay quầy chưa nhận.
     */
    public static final String ITEM_HANDED_OVER  = "ITEM_HANDED_OVER";
    public static final String ITEM_RECEIVED     = "ITEM_RECEIVED";
    public static final String ORDER_READY       = "ORDER_READY";   // cả đơn đã sẵn sàng
    public static final String ISSUE_OPENED      = "ISSUE_OPENED";
    public static final String ISSUE_RESOLVED    = "ISSUE_RESOLVED";
    public static final String ISSUE_UPDATED     = "ISSUE_UPDATED";
    /**
     * Sự cố báo nhầm, người báo tự thu hồi. Khác hẳn ISSUE_RESOLVED về ý nghĩa: một bên là
     * "đã xử lý xong", bên kia là "chưa từng có chuyện đó". Gộp chung một mã thì số liệu sự
     * cố của bếp phồng lên vì cả những lần bấm nhầm.
     */
    public static final String ISSUE_CANCELLED   = "ISSUE_CANCELLED";

    /**
     * Kế hoạch chuẩn bị sẵn trong ca. Ghi nhật ký vì đây là con số quyết định bao nhiêu nguyên
     * liệu bị dùng trước khi có đơn — làm dư thì đổ bỏ, làm thiếu thì khách chờ. Cuối ca cần
     * truy được ai đặt số và đã sửa mấy lần.
     */
    public static final String PREP_PLANNED      = "PREP_PLANNED";
    public static final String PREP_UPDATED      = "PREP_UPDATED";
    public static final String PREP_DONE         = "PREP_DONE";
    public static final String PREP_CANCELLED    = "PREP_CANCELLED";

    /**
     * Ca làm việc của thu ngân. Ghi nhật ký vì đóng ca là lúc một con số chênh lệch tiền mặt
     * được chốt lại — nếu về sau có tranh cãi, phải truy được ai đóng, lúc nào, và hệ thống
     * tính ra bao nhiêu so với số người đó đếm được.
     */
    public static final String SHIFT_OPENED      = "SHIFT_OPENED";
    public static final String SHIFT_CLOSED      = "SHIFT_CLOSED";
    public static final String SHIFT_CANCELLED   = "SHIFT_CANCELLED";

    // Pickup
    public static final String PICKUP_VERIFY_OK     = "PICKUP_VERIFY_OK";     // BR-15
    public static final String PICKUP_VERIFY_FAILED = "PICKUP_VERIFY_FAILED"; // case F mục 15
    public static final String HANDOFF              = "HANDOFF";

    /**
     * Đăng nhập, đăng xuất và đặt lại mật khẩu.
     * <p>
     * Tách riêng khỏi {@link #USER_CHANGED} chứ không gộp, cùng lý do với ISSUE_CANCELLED: đây là
     * nhóm duy nhất trả lời được câu hỏi "có ai đang dò mật khẩu tài khoản này không". Gộp vào
     * một mã chung thì dấu hiệu đó chìm giữa hàng trăm dòng sửa họ tên và đổi vai trò.
     * <p>
     * LOGIN_FAILED không có người thực hiện — chưa ai chứng minh được mình là ai cả — nên
     * {@code actor_id} để trống và email vừa gõ nằm ở {@code new_value}. Đó chính là thứ cần đọc
     * khi rà soát: cùng một email bị thử liên tiếp, hay nhiều email khác nhau bị quét lần lượt.
     */
    public static final String LOGIN_SUCCESS     = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED      = "LOGIN_FAILED";
    /** Vượt ngưỡng thử sai, cửa bị tạm khoá. Ghi một lần cho mỗi đợt, không ghi lại ở mọi lần thử. */
    public static final String LOGIN_BLOCKED     = "LOGIN_BLOCKED";
    public static final String LOGOUT            = "LOGOUT";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_DONE      = "PASSWORD_RESET_DONE";

    /**
     * Xác thực địa chỉ email: một mã gửi đi, và một địa chỉ được chủ nhân xác nhận.
     * <p>
     * Cặp này trả lời câu hỏi sẽ được hỏi đúng vào lúc có tranh chấp ai là chủ một địa chỉ:
     * thư đã gửi mấy lần, và người ta bấm liên kết lúc nào, từ máy nào. Bảng
     * {@code EmailVerificationToken} giữ chi tiết của từng mã, còn hai dòng nhật ký này nằm
     * chung một chỗ với lịch sử đăng nhập và đổi mật khẩu — nơi người rà soát thật sự tìm.
     */
    public static final String EMAIL_VERIFY_SENT = "EMAIL_VERIFY_SENT";
    public static final String EMAIL_VERIFIED    = "EMAIL_VERIFIED";

    // Admin
    public static final String PRODUCT_CHANGED   = "PRODUCT_CHANGED";
    public static final String CATEGORY_CHANGED  = "CATEGORY_CHANGED";
    public static final String USER_CHANGED      = "USER_CHANGED";

    /**
     * Ngừng bán và bán lại — thao tác Xoá (mềm) của hai màn hình quản trị danh mục.
     * <p>
     * Không gộp vào PRODUCT_CHANGED / CATEGORY_CHANGED cùng lý do với ISSUE_CANCELLED: sửa giá
     * một món và gỡ hẳn món đó khỏi thực đơn là hai việc khác nhau về hậu quả. Câu hỏi hay gặp
     * nhất khi rà soát thực đơn là "món này ai gỡ, lúc nào" — gộp một mã thì phải lọc tay giữa
     * hàng trăm dòng sửa giá mới trả lời được.
     */
    public static final String PRODUCT_RETIRED   = "PRODUCT_RETIRED";
    public static final String PRODUCT_RESTORED  = "PRODUCT_RESTORED";
    public static final String CATEGORY_RETIRED  = "CATEGORY_RETIRED";
    public static final String CATEGORY_RESTORED = "CATEGORY_RESTORED";

    /**
     * Chỉ tiêu doanh thu. Bảng RevenueTarget cho xoá hẳn, khác Shift — nên chính dòng nhật ký
     * này là thứ duy nhất còn lại sau khi bản ghi biến mất. Vì vậy TARGET_DELETED bắt buộc mang
     * theo con số cũ trong old_value: thiếu nó thì câu hỏi "chỉ tiêu tháng trước là bao nhiêu và
     * ai bỏ đi" không còn trả lời được ở đâu cả.
     */
    public static final String TARGET_CREATED    = "TARGET_CREATED";
    public static final String TARGET_UPDATED    = "TARGET_UPDATED";
    public static final String TARGET_DELETED    = "TARGET_DELETED";

    private AuditAction() {
    }
}
