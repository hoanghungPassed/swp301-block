package com.fastfood.controller.kitchen;

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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Các món bếp đã hoàn thành gần đây, kèm <b>sổ bàn giao ca</b>.
 * <p>
 * Hai thứ nằm chung một trang vì chúng trả lời cùng một câu hỏi lúc giao ca: <i>ca vừa rồi đã
 * làm những gì, và có chuyện gì ca sau cần biết</i>. Danh sách món trả lời vế đầu bằng số liệu,
 * sổ bàn giao trả lời vế sau bằng lời.
 */
@WebServlet("/kitchen/history")
public class KitchenHistoryServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();
    private final KitchenNoteService noteService = new KitchenNoteService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        // Lúc giao ca có hai câu hỏi khác nhau: cả bếp đã làm được gì, và tôi đã làm được gì.
        // Bộ lọc chỉ có một ô bật/tắt vì đó là toàn bộ khác biệt giữa hai câu đó.
        boolean mineOnly = "1".equals(WebUtil.getString(req, "mine"));
        req.setAttribute("mineOnly", mineOnly);
        req.setAttribute("pageData", kitchenService.recentReady(
                WebUtil.getInt(req, "page", 1), mineOnly ? user.getUserId() : 0));
        req.setAttribute("filterQuery", WebUtil.queryStringWithout(req, "page"));
        req.setAttribute("handovers", noteService.recentHandovers());
        req.setAttribute("today", LocalDate.now());

        int editId = WebUtil.getInt(req, "editNote", 0);
        if (editId > 0) {
            req.setAttribute("editingNote", noteService.findHandover(editId));
        }
        forward(req, resp, "kitchen/history.jsp");
    }

    /** Ba thao tác trên sổ bàn giao. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int noteId = WebUtil.getInt(req, "noteId", 0);
        String content = WebUtil.getString(req, "content");
        // Mang theo bộ lọc đang mở, nếu không thì ghi xong một dòng bàn giao là danh sách
        // nhảy về của cả bếp và người dùng tưởng ô lọc vừa bị tắt.
        String back = "/kitchen/history"
                + ("1".equals(WebUtil.getString(req, "mine")) ? "?mine=1" : "");

        switch (WebUtil.getString(req, "action") == null ? "" : WebUtil.getString(req, "action")) {
            case "noteUpdate":
                handle(req, resp, () -> noteService.updateHandover(noteId, user.getUserId(), content),
                        "Đã sửa dòng bàn giao.", back);
                return;
            case "noteDelete":
                handle(req, resp, () -> noteService.deleteHandover(noteId, user.getUserId()),
                        "Đã xoá dòng bàn giao.", back);
                return;
            case "noteAdd":
            default:
                handle(req, resp, () -> noteService.addHandover(
                                parseDate(WebUtil.getString(req, "shiftDate")),
                                user.getUserId(), content),
                        "Đã ghi vào sổ bàn giao.", back);
        }
    }

    /** Ngày hỏng thì lùi về hôm nay — đây là ô chọn ngày, không phải dữ liệu bắt buộc đúng. */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }
}
