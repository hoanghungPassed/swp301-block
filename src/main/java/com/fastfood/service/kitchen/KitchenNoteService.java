package com.fastfood.service.kitchen;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.kitchen.KitchenNoteDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.model.entity.KitchenNote;
import com.fastfood.model.entity.OrderItemNote;

import com.fastfood.service.Tx;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Hai loại ghi chú của bếp: ghi chú theo món, và sổ bàn giao ca.
 * <p>
 * <b>Vì sao không dùng lại sự cố bếp cho việc này.</b> Số sự cố đang mở điều khiển bốn chỗ cảnh
 * báo đỏ trên màn hình thu ngân. Một dòng "khách dặn ít cay" đi vào đó sẽ hiện thành sự cố chưa
 * xử lý ở cả bốn chỗ, và cảnh báo mất hết ý nghĩa. Sự cố là chuyện <i>phải giải quyết</i>; ghi
 * chú chỉ là thông tin để lại.
 * <p>
 * Vì không dính tiền, không đổi trạng thái đơn và không có dòng nhật ký nào trỏ về, ghi chú là
 * dữ liệu <b>duy nhất ngoài giỏ hàng được xoá hẳn</b> khỏi cơ sở dữ liệu.
 */
public class KitchenNoteService {

    /** Số ngày sổ bàn giao nhìn lại. Ca sáng cần đọc lại bàn giao của tối hôm trước. */
    private static final int HANDOVER_LOOKBACK_DAYS = 7;

    private static final int MAX_ITEM_NOTE = 500;
    private static final int MAX_SHIFT_NOTE = 1000;

    private final KitchenNoteDAO noteDAO = new KitchenNoteDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();

    // ============================================================ ghi chú theo món

    public List<OrderItemNote> notesOfItem(int orderItemId) {
        return Tx.read(con -> noteDAO.findNotesOfItem(con, orderItemId));
    }

    /**
     * Thêm ghi chú cho một món.
     * Món phải có thật — ghi chú mồ côi không hiện ở đâu cả nhưng vẫn chiếm một dòng.
     */
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

    // ============================================================ sổ bàn giao ca

    /** Bàn giao của bảy ngày gần nhất, mới nhất trước. */
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

    /**
     * Viết một dòng bàn giao.
     * <p>
     * Ngày mặc định là hôm nay và không nhận ngày tương lai: bàn giao là ghi lại chuyện đã xảy
     * ra trong ca, không phải kế hoạch — kế hoạch đã có {@link PrepService}.
     */
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

    // ============================================================ dùng chung

    /**
     * Đọc trước để báo lỗi cho đúng.
     * <p>
     * Câu lệnh ghi đã gộp điều kiện "đúng người viết" vào chính nó, nên khi nó trả về 0 dòng thì
     * không phân biệt được ghi chú không tồn tại hay của người khác. Hai chuyện đó cần hai câu
     * trả lời khác nhau.
     */
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
