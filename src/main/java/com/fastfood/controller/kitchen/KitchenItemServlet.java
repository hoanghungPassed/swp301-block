package com.fastfood.controller.kitchen;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.kitchen.KitchenNoteService;
import com.fastfood.service.kitchen.KitchenService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Chi tiết một món cần chế biến, kèm lịch sử sự cố và <b>ghi chú chế biến</b> của món đó.
 * <p>
 * Hai khối tách bạch vì chúng khác nhau về hệ quả: sự cố hiện thành cảnh báo đỏ trên màn hình
 * thu ngân và phải có người xử lý; ghi chú chỉ là thông tin để lại cho ca sau — xem
 * {@link KitchenNoteService}.
 */
@WebServlet("/kitchen/item")
public class KitchenItemServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();
    private final KitchenNoteService noteService = new KitchenNoteService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        int itemId = WebUtil.getInt(req, "id", 0);
        try {
            req.setAttribute("item", kitchenService.findItem(itemId));
            req.setAttribute("issues", kitchenService.issuesOfItem(itemId));
            req.setAttribute("notes", noteService.notesOfItem(itemId));

            int editId = WebUtil.getInt(req, "editNote", 0);
            if (editId > 0) {
                req.setAttribute("editingNote", findEditableNote(itemId, editId));
            }
            forward(req, resp, "kitchen/item-detail.jsp");
        } catch (AppException e) {
            req.setAttribute("errorMessage", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            forward(req, resp, "error/404.jsp");
        }
    }

    /**
     * Ghi chú đang sửa phải thuộc đúng món đang mở. Không kiểm thì gõ tay một mã ghi chú của
     * món khác sẽ hiện nội dung đó lên đây, và bấm lưu là ghi đè nhầm chỗ.
     */
    private Object findEditableNote(int itemId, int noteId) {
        return noteService.notesOfItem(itemId).stream()
                .filter(n -> n.getNoteId() == noteId)
                .findFirst()
                .orElse(null);
    }

    /** Ba thao tác trên ghi chú. Sự cố vẫn đi qua {@code /kitchen/issue} như trước. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int itemId = WebUtil.getInt(req, "orderItemId", 0);
        int noteId = WebUtil.getInt(req, "noteId", 0);
        String content = WebUtil.getString(req, "content");
        String back = "/kitchen/item?id=" + itemId;

        switch (WebUtil.getString(req, "action") == null ? "" : WebUtil.getString(req, "action")) {
            case "noteUpdate":
                handle(req, resp, () -> noteService.updateItemNote(noteId, user.getUserId(), content),
                        "Đã sửa ghi chú.", back);
                return;
            case "noteDelete":
                handle(req, resp, () -> noteService.deleteItemNote(noteId, user.getUserId()),
                        "Đã xoá ghi chú.", back);
                return;
            case "noteAdd":
            default:
                handle(req, resp, () -> noteService.addItemNote(itemId, user.getUserId(), content),
                        "Đã thêm ghi chú.", back);
        }
    }
}
