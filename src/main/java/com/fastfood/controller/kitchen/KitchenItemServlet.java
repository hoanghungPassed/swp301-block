package com.fastfood.controller.kitchen;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.OrderEntities.OrderItem;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.kitchen.KitchenNoteService;
import com.fastfood.service.kitchen.KitchenService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/kitchen/item")
public class KitchenItemServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();
    private final KitchenNoteService noteService = new KitchenNoteService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        int itemId = WebUtil.getInt(req, "id", 0);
        try {
            OrderItem item = kitchenService.findItem(itemId);
            req.setAttribute("item", item);
            /* Người giữ cả đơn, không phải người nhận riêng món này: nút nhận ở dưới nhận trọn
               đơn nên phải nói theo đơn, đừng để bếp bấm rồi mới biết đơn đã có chủ. */
            req.setAttribute("orderHolder", kitchenService.holderOfOrder(item.getOrderId()));
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

    private Object findEditableNote(int itemId, int noteId) {
        return noteService.notesOfItem(itemId).stream()
                .filter(n -> n.getNoteId() == noteId)
                .findFirst()
                .orElse(null);
    }

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
