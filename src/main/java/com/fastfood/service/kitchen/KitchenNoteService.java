package com.fastfood.service.kitchen;

import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.kitchen.KitchenNoteDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.model.entity.OperationEntities.KitchenNote;
import com.fastfood.model.entity.OrderEntities.OrderItemNote;

import com.fastfood.service.Tx;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class KitchenNoteService {

    private static final int HANDOVER_LOOKBACK_DAYS = 7;

    private static final int MAX_ITEM_NOTE = 500;
    private static final int MAX_SHIFT_NOTE = 1000;

    private final KitchenNoteDAO noteDAO = new KitchenNoteDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();

    public List<OrderItemNote> notesOfItem(int orderItemId) {
        return Tx.read(con -> noteDAO.findNotesOfItem(con, orderItemId));
    }

    public OrderItemNote addItemNote(int orderItemId, int authorId, String content) {
        String text = requireText(content, MAX_ITEM_NOTE);
        LocalDateTime now = DateTimeUtil.now();
        return Tx.write(con -> {
            if (orderItemDAO.findById(con, orderItemId) == null) {
                throw new NotFoundException("Không tìm thấy món.");
            }
            OrderItemNote note = new OrderItemNote();
            note.setOrderItemId(orderItemId);
            note.setAuthorId(authorId);
            note.setContent(text);
            note.setCreatedAt(now);
            noteDAO.insertItemNote(con, note);
            return note;
        });
    }

    public void updateItemNote(int noteId, int authorId, String content) {
        String text = requireText(content, MAX_ITEM_NOTE);
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwnItemNote(con, noteId, authorId);
            noteDAO.updateItemNote(con, noteId, authorId, text, now);
        });
    }

    public void deleteItemNote(int noteId, int authorId) {
        Tx.writeVoid(con -> {
            requireOwnItemNote(con, noteId, authorId);
            noteDAO.deleteItemNote(con, noteId, authorId);
        });
    }

    public List<KitchenNote> recentHandovers() {
        return Tx.read(con -> noteDAO.findRecentShiftNotes(con, HANDOVER_LOOKBACK_DAYS));
    }

    public KitchenNote findHandover(int kitchenNoteId) {
        KitchenNote note = Tx.read(con -> noteDAO.findShiftNote(con, kitchenNoteId));
        if (note == null) {
            throw new NotFoundException("Không tìm thấy dòng bàn giao.");
        }
        return note;
    }

    public KitchenNote addHandover(LocalDate shiftDate, int authorId, String content) {
        String text = requireText(content, MAX_SHIFT_NOTE);
        LocalDateTime now = DateTimeUtil.now();
        LocalDate day = shiftDate == null ? now.toLocalDate() : shiftDate;
        if (day.isAfter(now.toLocalDate())) {
            throw new ValidationException("Không ghi bàn giao cho ngày chưa tới.");
        }
        return Tx.write(con -> {
            KitchenNote note = new KitchenNote();
            note.setShiftDate(day);
            note.setAuthorId(authorId);
            note.setContent(text);
            note.setCreatedAt(now);
            noteDAO.insertShiftNote(con, note);
            return note;
        });
    }

    public void updateHandover(int kitchenNoteId, int authorId, String content) {
        String text = requireText(content, MAX_SHIFT_NOTE);
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwnHandover(con, kitchenNoteId, authorId);
            noteDAO.updateShiftNote(con, kitchenNoteId, authorId, text, now);
        });
    }

    public void deleteHandover(int kitchenNoteId, int authorId) {
        Tx.writeVoid(con -> {
            requireOwnHandover(con, kitchenNoteId, authorId);
            noteDAO.deleteShiftNote(con, kitchenNoteId, authorId);
        });
    }

    private void requireOwnItemNote(java.sql.Connection con, int noteId, int authorId)
            throws java.sql.SQLException {
        OrderItemNote note = noteDAO.findItemNote(con, noteId);
        if (note == null) {
            throw new NotFoundException("Không tìm thấy ghi chú.");
        }
        if (note.getAuthorId() != authorId) {
            throw new BusinessException("Chỉ người đã viết mới sửa hoặc xoá được ghi chú này.");
        }
    }

    private void requireOwnHandover(java.sql.Connection con, int kitchenNoteId, int authorId)
            throws java.sql.SQLException {
        KitchenNote note = noteDAO.findShiftNote(con, kitchenNoteId);
        if (note == null) {
            throw new NotFoundException("Không tìm thấy dòng bàn giao.");
        }
        if (note.getAuthorId() != authorId) {
            throw new BusinessException("Chỉ người đã viết mới sửa hoặc xoá được dòng này.");
        }
    }

    private String requireText(String content, int max) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new ValidationException("Vui lòng nhập nội dung ghi chú.");
        }
        if (text.length() > max) {
            throw new ValidationException("Ghi chú tối đa " + max + " ký tự.");
        }
        return text;
    }
}
