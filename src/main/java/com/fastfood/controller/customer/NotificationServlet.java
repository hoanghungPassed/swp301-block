package com.fastfood.controller.customer;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.shared.NotificationService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Hộp thông báo của khách.
 * <p>
 * Hệ thống đã sinh tin cho bốn sự kiện quan trọng nhất của một đơn — xác nhận, sẵn sàng, bị
 * huỷ, hết hiệu lực — nhưng bản chạy thử gửi qua kênh giả lập, nghĩa là không có bức thư nào
 * thật sự tới tay ai. Trước màn hình này, những tin đó chỉ nằm trong cơ sở dữ liệu: khách bị
 * huỷ đơn kèm hoàn tiền không có chỗ nào đọc được chuyện đó ngoài việc nhớ mã đơn rồi tự mở
 * trang theo dõi.
 * <p>
 * Không phân quyền theo vai trò ở đây vì tin gắn với người nhận chứ không gắn với vai trò:
 * lọc theo {@code user_id} nằm trong chính câu truy vấn, nên mỗi tài khoản chỉ đọc được tin
 * của mình. Nhân viên mở địa chỉ này chỉ thấy hộp rỗng, đúng như thực tế.
 */
@WebServlet("/notifications")
public class NotificationServlet extends BaseServlet {

    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        req.setAttribute("pageData",
                notificationService.pageOfUser(user.getUserId(), WebUtil.getInt(req, "page", 1)));
        req.setAttribute("filterQuery", WebUtil.queryStringWithout(req, "page"));
        forward(req, resp, "customer/notifications.jsp");
    }

    /**
     * Đánh dấu đã đọc toàn bộ.
     * <p>
     * Cố tình <b>không</b> tự đánh dấu khi khách chỉ mở trang: danh sách dài hơn một màn hình
     * thì mở trang không có nghĩa là đã đọc hết, và tin quan trọng nhất — báo hoàn tiền — lại
     * hay nằm dưới cùng. Tin nào khách thật sự xem thì được đánh dấu ở chỗ họ xem, tại trang
     * theo dõi đơn.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        handle(req, resp, () -> notificationService.markAllRead(user.getUserId()),
                "Đã đánh dấu toàn bộ thông báo là đã đọc.", "/notifications");
    }
}
