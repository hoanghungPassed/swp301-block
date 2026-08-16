package com.fastfood.flow;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.entity.KitchenNote;
import com.fastfood.model.entity.OrderItemNote;
import com.fastfood.service.kitchen.KitchenNoteService;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hai loại ghi chú của bếp.
 * <p>
 * Điều quan trọng nhất được kiểm ở đây không phải bốn thao tác chạy được, mà là <b>ghi chú không
 * làm tăng số sự cố đang mở</b>. Con số đó điều khiển bốn chỗ cảnh báo đỏ trên màn hình thu ngân;
 * nếu ghi chú thường ngày lọt vào đó thì cảnh báo mất hết ý nghĩa.
 */
@DisplayName("Ghi chú chế biến và sổ bàn giao ca bếp")
class KitchenNoteIT extends IntegrationTestBase {

    private final KitchenNoteService noteService = new KitchenNoteService();
    private final KitchenService kitchenService = new KitchenService();

    /** Một món có thật trong dữ liệu mẫu để gắn ghi chú vào. */
    private static int anyOrderItemId() {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 order_item_id FROM dbo.OrderItem ORDER BY order_item_id");
        if (id == null) {
            throw new IllegalStateException("Du lieu mau khong co dong OrderItem nao");
        }
        return id;
    }

    @Nested
    @DisplayName("Ghi chú theo món")
    class ItemNotes {

        @Test
        @DisplayName("Thêm rồi đọc lại được, chưa sửa thì không có dấu đã sửa")
        void addThenRead() {
            int itemId = anyOrderItemId();
            int before = noteService.notesOfItem(itemId).size();

            OrderItemNote note = noteService.addItemNote(itemId, userId(KITCHEN_1),
                    "  Khách dặn ít cay  ");

            assertTrue(note.getNoteId() > 0);
            assertEquals("Khách dặn ít cay", note.getContent(), "Phải cắt khoảng trắng thừa");
            assertFalse(note.isEdited());
            assertEquals(before + 1, noteService.notesOfItem(itemId).size());
        }

        @Test
        @DisplayName("Sửa xong thì có dấu đã sửa")
        void updateMarksEdited() {
            int itemId = anyOrderItemId();
            OrderItemNote note = noteService.addItemNote(itemId, userId(KITCHEN_1), "bản đầu");

            noteService.updateItemNote(note.getNoteId(), userId(KITCHEN_1), "bản đã sửa");

            OrderItemNote after = noteService.notesOfItem(itemId).stream()
                    .filter(n -> n.getNoteId() == note.getNoteId()).findFirst().orElseThrow();
            assertEquals("bản đã sửa", after.getContent());
            assertTrue(after.isEdited(), "Người đọc cần biết nội dung đã bị thay");
        }

        @Test
        @DisplayName("Xoá là xoá hẳn khỏi bảng, không phải đổi trạng thái")
        void deleteRemovesTheRow() {
            int itemId = anyOrderItemId();
            OrderItemNote note = noteService.addItemNote(itemId, userId(KITCHEN_1), "sẽ xoá");

            noteService.deleteItemNote(note.getNoteId(), userId(KITCHEN_1));

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.OrderItemNote WHERE note_id = ?",
                            note.getNoteId()),
                    "Ghi chú không dính tiền và không có nhật ký trỏ về nên xoá hẳn được");
        }

        @Test
        @DisplayName("Chỉ người viết mới sửa hoặc xoá được")
        void onlyAuthorMayEdit() {
            int itemId = anyOrderItemId();
            OrderItemNote note = noteService.addItemNote(itemId, userId(KITCHEN_1), "của bếp 1");
            int id = note.getNoteId();
            int other = userId(KITCHEN_2);

            assertThrows(BusinessException.class, () -> noteService.updateItemNote(id, other, "sửa trộm"));
            assertThrows(BusinessException.class, () -> noteService.deleteItemNote(id, other));
            assertEquals(1, count("SELECT COUNT(*) FROM dbo.OrderItemNote WHERE note_id = ?", id));
        }

        @Test
        @DisplayName("Nội dung rỗng bị từ chối, món không tồn tại cũng vậy")
        void invalidInputIsRejected() {
            int itemId = anyOrderItemId();
            assertThrows(ValidationException.class,
                    () -> noteService.addItemNote(itemId, userId(KITCHEN_1), "   "));
            assertThrows(NotFoundException.class,
                    () -> noteService.addItemNote(999_999, userId(KITCHEN_1), "món ma"));
        }
    }

    @Test
    @DisplayName("Ghi chú KHÔNG làm tăng số sự cố đang mở của bếp")
    void notesDoNotInflateTheIssueBadge() {
        int itemId = anyOrderItemId();
        int issuesBefore = kitchenService.countOpenIssues();

        noteService.addItemNote(itemId, userId(KITCHEN_1), "ghi chú thường ngày");

        assertEquals(issuesBefore, kitchenService.countOpenIssues(),
                "Số này điều khiển bốn chỗ cảnh báo đỏ trên màn hình thu ngân — "
                        + "ghi chú lọt vào đó là cảnh báo mất ý nghĩa");
    }

    @Nested
    @DisplayName("Sổ bàn giao ca")
    class Handover {

        @Test
        @DisplayName("Ghi cho hôm nay rồi đọc lại thấy trong danh sách gần đây")
        void writeThenRead() {
            KitchenNote note = noteService.addHandover(null, userId(KITCHEN_1),
                    "Lò số 2 nóng chậm");

            assertEquals(LocalDate.now(), note.getShiftDate(), "Không chọn ngày thì mặc định hôm nay");
            assertTrue(noteService.recentHandovers().stream()
                    .anyMatch(n -> n.getKitchenNoteId() == note.getKitchenNoteId()));
        }

        @Test
        @DisplayName("Không ghi bàn giao cho ngày chưa tới")
        void futureDateIsRejected() {
            assertThrows(ValidationException.class,
                    () -> noteService.addHandover(LocalDate.now().plusDays(1), userId(KITCHEN_1),
                            "chuyện chưa xảy ra"));
        }

        @Test
        @DisplayName("Ngày cũ vẫn ghi được — ca đêm viết bàn giao sau nửa đêm")
        void pastDateIsAllowed() {
            KitchenNote note = noteService.addHandover(LocalDate.now().minusDays(1),
                    userId(KITCHEN_2), "bàn giao ca tối hôm qua");

            assertEquals(LocalDate.now().minusDays(1), note.getShiftDate());
        }

        @Test
        @DisplayName("Sửa và xoá được, và chỉ người viết mới làm được")
        void updateDeleteAndOwnership() {
            KitchenNote note = noteService.addHandover(null, userId(KITCHEN_1), "bản đầu");
            int id = note.getKitchenNoteId();
            int other = userId(KITCHEN_2);

            assertThrows(BusinessException.class, () -> noteService.updateHandover(id, other, "trộm"));
            assertThrows(BusinessException.class, () -> noteService.deleteHandover(id, other));

            noteService.updateHandover(id, userId(KITCHEN_1), "bản đã sửa");
            assertEquals("bản đã sửa", noteService.findHandover(id).getContent());

            noteService.deleteHandover(id, userId(KITCHEN_1));
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.KitchenNote WHERE kitchen_note_id = ?", id));
        }

        @Test
        @DisplayName("Chỉ lấy bàn giao trong bảy ngày gần nhất")
        void lookbackIsBounded() {
            exec("INSERT INTO dbo.KitchenNote (shift_date, author_id, content) VALUES (?, ?, ?)",
                    java.sql.Date.valueOf(LocalDate.now().minusDays(30)), userId(KITCHEN_1),
                    "bàn giao rất cũ");

            List<KitchenNote> recent = noteService.recentHandovers();

            assertTrue(recent.stream().noneMatch(n -> "bàn giao rất cũ".equals(n.getContent())),
                    "Sổ bàn giao dài vô hạn thì không ai đọc nữa");
        }
    }
}
