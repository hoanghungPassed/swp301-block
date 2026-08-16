package com.fastfood.controller.staff;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.service.staff.CounterRejectService;
import com.fastfood.service.staff.StaffOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Quầy giao nhận — nơi món đi từ bếp ra tới tay khách.
 * <p>
 * Màn hình này trước đây chỉ hiện sự cố bếp và hoàn toàn chỉ đọc. Nay nó là một vị trí làm
 * việc thật: thu ngân nhận món bếp vừa đưa ra, và nhìn thấy đơn nào đã đủ món để gọi khách.
 * Gộp chung với sự cố bếp là đúng chỗ — cả ba khối đều trả lời một câu hỏi duy nhất mà người
 * đứng quầy hỏi cả ngày: món của đơn này đang ở đâu.
 * <p>
 * Riêng sự cố vẫn chỉ đọc. Bếp là nơi phát hiện và cũng là nơi đánh dấu đã xử lý, nhưng người
 * phải giải thích với khách lại đứng ở đây — nên thông tin bắt buộc phải sang được bên này.
 * <p>
 * Thu ngân có đúng hai đường xử lý sự cố, và cả hai đều nằm ở màn hình chi tiết đơn: chờ bếp
 * làm xong, hoặc huỷ đơn kèm hoàn tiền. <b>Không có đổi món</b> — đổi món kéo theo chênh lệch
 * giá, thu thêm hoặc hoàn bớt, mà hệ thống chỉ hoàn tiền toàn phần. Vì vậy mỗi dòng sự cố ở
 * đây đều dẫn thẳng sang đơn tương ứng.
 */
@WebServlet("/staff/counter")
public class CounterServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();
    private final StaffOrderService orderService = new StaffOrderService();
    private final CounterRejectService rejectService = new CounterRejectService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setAttribute("awaitingCounter", orderService.awaitingCounter());
        // Kèm danh sách món để nói được đơn nào còn thiếu — bản rút gọn của màn hình điều phối
        // không nạp món nên không trả lời được câu đó.
        req.setAttribute("readyOrders", orderService.readyOrdersForCounter());
        req.setAttribute("openIssues", kitchenService.openIssues());
        req.setAttribute("recentIssues", kitchenService.recentIssues(30));
        // Phiếu do chính quầy lập, tách khỏi sự cố bếp: hai bên nhìn cùng một bảng
        // nhưng cần thấy phần của mình trước.
        req.setAttribute("counterRejects", rejectService.openRejects());
        forward(req, resp, "staff/counter.jsp");
    }

    /**
     * Hai đường ra cho một món bếp vừa đưa lên quầy: <b>nhận</b>, hoặc <b>từ chối</b>.
     * <p>
     * Trước đây chỉ có đường nhận. Thu ngân phát hiện món sai hay nguội thì không có nút nào để
     * trả về bếp — họ buộc phải nhận rồi đi nói miệng, và chuyện đó không để lại dấu vết.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User cashier = requireUser(req);
        int itemId = WebUtil.getInt(req, "orderItemId", 0);
        int issueId = WebUtil.getInt(req, "issueId", 0);
        String reason = WebUtil.getString(req, "reason");
        String action = WebUtil.getString(req, "action");
        String back = "/staff/counter";

        switch (action == null ? "" : action) {
            case "reject":
                handle(req, resp, () -> rejectService.reject(itemId, cashier.getUserId(), reason),
                        "Đã trả món về bếp kèm lý do. Món sẽ hiện lại ở màn hình bếp để làm lại.",
                        back);
                return;
            case "rejectUpdate":
                handle(req, resp, () -> rejectService.updateReason(issueId, cashier.getUserId(), reason),
                        "Đã sửa lý do từ chối.", back);
                return;
            case "rejectCancel":
                handle(req, resp, () -> rejectService.cancel(issueId, cashier.getUserId()),
                        "Đã thu hồi phiếu từ chối.", back);
                return;
            case "receive":
            default:
                handle(req, resp, () -> orderService.receiveAtCounter(itemId, cashier.getUserId()),
                        "Đã nhận món từ bếp.", back);
        }
    }
}
