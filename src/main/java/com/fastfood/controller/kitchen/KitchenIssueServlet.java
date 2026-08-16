package com.fastfood.controller.kitchen;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.KdsItemView;
import com.fastfood.model.entity.KitchenIssue;
import com.fastfood.model.entity.User;
import com.fastfood.service.kitchen.KitchenService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sự cố bếp: hết nguyên liệu, món hỏng phải làm lại.
 * Ghi nhận sự cố không làm món lùi về hàng chờ — nếu lùi thì người khác có thể
 * nhận lại món đang có người làm dở.
 */
@WebServlet("/kitchen/issue")
public class KitchenIssueServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setAttribute("openIssues", kitchenService.openIssues());
        // Chỉ giữ những sự cố đã khép lại. Trước đây trang nhận cả danh sách rồi lọc trong
        // vòng lặp hiển thị, nên bảng "đã khép lại" không bao giờ hiện được dòng "chưa có gì":
        // danh sách vẫn đầy, chỉ là mọi phần tử đều bị bỏ qua lúc vẽ.
        List<KitchenIssue> closed = new ArrayList<>();
        for (KitchenIssue issue : kitchenService.recentIssues(30)) {
            if (!issue.isOpen()) {
                closed.add(issue);
            }
        }
        req.setAttribute("closedIssues", closed);

        // Ô chọn món của biểu mẫu báo sự cố. Đi từ thẻ trên màn hình bếp thì món đã được
        // chọn sẵn; vào thẳng trang này thì đầu bếp chọn trong danh sách chứ không phải
        // tự nhớ mã món như trước.
        int orderItemId = WebUtil.getInt(req, "orderItemId", 0);
        req.setAttribute("orderItemId", orderItemId);
        req.setAttribute("kitchenItems", withRequestedItem(kitchenService.itemsInKitchen(), orderItemId));

        // ?edit= đổi biểu mẫu bên phải từ "báo mới" sang "sửa mô tả", giống cách hai màn hình
        // quản trị món ăn và nhóm món đang làm — cùng một trang, không mở thêm cửa sổ.
        int editId = WebUtil.getInt(req, "edit", 0);
        if (editId > 0) {
            req.setAttribute("editing", kitchenService.findIssue(editId));
        }
        forward(req, resp, "kitchen/issue.jsp");
    }

    /**
     * Bổ sung món được chỉ đích danh vào đầu danh sách chọn nếu nó không còn nằm trong bếp.
     * <p>
     * Trang chi tiết món dẫn tới đây kèm mã của bất kỳ món nào, kể cả món đã bàn giao ra quầy.
     * Thiếu bước này thì ô chọn không có mục nào khớp, trình duyệt tự chọn mục đầu tiên, và
     * đầu bếp bấm gửi là ghi sự cố lên một món hoàn toàn khác — im lặng và sai.
     */
    private List<KdsItemView> withRequestedItem(List<KdsItemView> items, int orderItemId) {
        if (orderItemId <= 0) {
            return items;
        }
        for (KdsItemView view : items) {
            if (view.getItem().getOrderItemId() == orderItemId) {
                return items;
            }
        }
        List<KdsItemView> withIt = new ArrayList<>(items.size() + 1);
        try {
            withIt.add(new KdsItemView(kitchenService.findItem(orderItemId)));
        } catch (AppException e) {
            // Mã món gõ tay hoặc đã bị xoá. Không có gì để thêm, và ô chọn để trống là đúng —
            // JSP sẽ không chọn sẵn mục nào.
            return items;
        }
        withIt.addAll(items);
        return withIt;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        String action = WebUtil.getString(req, "action");

        int issueId = WebUtil.getInt(req, "issueId", 0);

        switch (action == null ? "" : action) {
            case "resolve":
                handle(req, resp, () -> kitchenService.resolveIssue(issueId, user.getUserId()),
                        "Đã đánh dấu sự cố được xử lý.", "/kitchen/issue");
                return;
            case "update":
                handle(req, resp, () -> kitchenService.updateIssue(issueId, user.getUserId(),
                                WebUtil.getString(req, "description")),
                        "Đã cập nhật mô tả sự cố.", "/kitchen/issue");
                return;
            case "cancel":
                handle(req, resp, () -> kitchenService.cancelIssue(issueId, user.getUserId()),
                        "Đã thu hồi sự cố báo nhầm.", "/kitchen/issue");
                return;
            default:
                break;
        }

        int orderItemId = WebUtil.getInt(req, "orderItemId", 0);
        String issueType = WebUtil.getString(req, "issueType");
        String description = WebUtil.getString(req, "description");
        handle(req, resp, () -> kitchenService.openIssue(orderItemId, user.getUserId(), issueType, description),
                "Đã ghi nhận sự cố.", "/kitchen/issue");
    }
}
