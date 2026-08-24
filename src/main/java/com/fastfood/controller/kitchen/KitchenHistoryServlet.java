package com.fastfood.controller.kitchen;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.kitchen.KitchenNoteService;
import com.fastfood.service.kitchen.KitchenService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@WebServlet("/kitchen/history")
public class KitchenHistoryServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();
    private final KitchenNoteService noteService = new KitchenNoteService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        boolean mineOnly = "1".equals(WebUtil.getString(req, "mine"));
        req.setAttribute("mineOnly", mineOnly);
        req.setAttribute("pageData", kitchenService.recentReady(
                WebUtil.getInt(req, "page", 1), mineOnly ? user.getUserId() : 0));
        req.setAttribute("kitchenNotes", noteService.recentNotes());
        req.setAttribute("today", DateTimeUtil.now().toLocalDate());

        int editId = WebUtil.getInt(req, "editNote", 0);
        if (editId > 0) {
            req.setAttribute("editingNote", noteService.findNote(editId));
        }
        forward(req, resp, "kitchen/history.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int noteId = WebUtil.getInt(req, "noteId", 0);
        String content = WebUtil.getString(req, "content");
        String back = "/kitchen/history"
                + ("1".equals(WebUtil.getString(req, "mine")) ? "?mine=1" : "");

        switch (WebUtil.getString(req, "action") == null ? "" : WebUtil.getString(req, "action")) {
            case "noteUpdate":
                handle(req, resp, () -> noteService.updateNote(noteId, user.getUserId(), content),
                        "Đã sửa ghi chú bếp.", back);
                return;
            case "noteDelete":
                handle(req, resp, () -> noteService.deleteNote(noteId, user.getUserId()),
                        "Đã xoá ghi chú bếp.", back);
                return;
            case "noteAdd":
            default:
                handle(req, resp, () -> noteService.addNote(
                                parseDate(WebUtil.getString(req, "shiftDate")),
                                user.getUserId(), content),
                        "Đã thêm ghi chú bếp.", back);
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return DateTimeUtil.now().toLocalDate();
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return DateTimeUtil.now().toLocalDate();
        }
    }
}
