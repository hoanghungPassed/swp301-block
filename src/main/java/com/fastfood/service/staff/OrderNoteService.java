package com.fastfood.service.staff;

import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.staff.OrderNoteDAO;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.OrderNote;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class OrderNoteService {

    private static final int MAX_LENGTH = 500;

    private final OrderNoteDAO noteDAO = new OrderNoteDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    public List<OrderNote> notesOf(int orderId) {
        return Tx.read(con -> noteDAO.findByOrder(con, orderId));
    }

    public Map<Integer, List<OrderNote>> notesOfOrders(List<Integer> orderIds) {
        return Tx.read(con -> noteDAO.findByOrders(con, orderIds));
    }

    public OrderNote add(int orderId, int authorId, String content) {
        String text = requireText(content);
        LocalDateTime now = DateTimeUtil.now();
        return Tx.write(con -> {
            Order order = orderDAO.findById(con, orderId);
            if (order == null) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            OrderNote note = new OrderNote();
            note.setOrderId(orderId);
            note.setAuthorId(authorId);
            note.setContent(text);
            note.setCreatedAt(now);
            noteDAO.insert(con, note);
            return note;
        });
    }

    public void update(int orderNoteId, int authorId, String content) {
        String text = requireText(content);
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwn(con, orderNoteId, authorId);
            noteDAO.update(con, orderNoteId, authorId, text, now);
        });
    }

    public void delete(int orderNoteId, int authorId) {
        Tx.writeVoid(con -> {
            requireOwn(con, orderNoteId, authorId);
            noteDAO.delete(con, orderNoteId, authorId);
        });
    }

    private void requireOwn(Connection con, int orderNoteId, int authorId) throws SQLException {
        OrderNote note = noteDAO.findById(con, orderNoteId);
        if (note == null) {
            throw new NotFoundException("Không tìm thấy ghi chú.");
        }
        if (note.getAuthorId() != authorId) {
            throw new BusinessException("Chỉ người đã viết mới sửa hoặc xoá được ghi chú này.");
        }
    }

    private String requireText(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new ValidationException("Vui lòng nhập nội dung ghi chú.");
        }
        if (text.length() > MAX_LENGTH) {
            throw new ValidationException("Ghi chú tối đa " + MAX_LENGTH + " ký tự.");
        }
        return text;
    }
}
