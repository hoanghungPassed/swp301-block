package com.fastfood.flow;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.entity.PrepTask;
import com.fastfood.service.kitchen.PrepService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kế hoạch chuẩn bị sẵn của bếp — phần việc duy nhất của bếp không bắt nguồn từ đơn hàng.
 * <p>
 * Điều đáng kiểm nhất không phải bốn thao tác chạy được, mà là <b>ba chốt chặn</b>: một món
 * một dòng mỗi ngày, dòng đã khép lại thì không sửa được nữa, và chỉ người lập mới thu hồi được.
 */
@DisplayName("Bếp lập kế hoạch chuẩn bị sẵn trong ca")
class KitchenPrepIT extends IntegrationTestBase {

    private final PrepService prepService = new PrepService();

    /** Ngày riêng cho mỗi bài để hai bài không tranh nhau ràng buộc "một món một ngày". */
    private static LocalDate dayOffset(int days) {
        return LocalDate.now().plusDays(days);
    }

    @Nested
    @DisplayName("Bốn thao tác")
    class Crud {

        @Test
        @DisplayName("Lập rồi đọc lại được, mặc định chưa làm phần nào")
        void planThenRead() {
            LocalDate day = dayOffset(1);
            int productId = anyOrderableProductId();

            PrepTask created = prepService.plan(productId, day, 20, "nướng sẵn trước 11h",
                    userId(KITCHEN_1));

            assertTrue(created.getPrepTaskId() > 0);
            PrepTask loaded = prepService.findById(created.getPrepTaskId());
            assertEquals(20, loaded.getPlannedQty());
            assertEquals(0, loaded.getDoneQty(), "Vừa lập thì chưa làm phần nào");
            assertEquals(20, loaded.getRemainingQty());
            assertEquals("PLANNED", loaded.getStatus());
            assertNotNull(loaded.getProductName(), "Phải kèm tên món để hiện thẳng lên bảng");
        }

        @Test
        @DisplayName("Sửa được cả số dự kiến lẫn số đã làm")
        void updateBothQuantities() {
            LocalDate day = dayOffset(2);
            PrepTask task = prepService.plan(anyOrderableProductId(), day, 30, null, userId(KITCHEN_1));

            prepService.update(task.getPrepTaskId(), 25, 18, "hụt vì hết khay", userId(KITCHEN_1));

            PrepTask after = prepService.findById(task.getPrepTaskId());
            assertEquals(25, after.getPlannedQty());
            assertEquals(18, after.getDoneQty());
            assertEquals(7, after.getRemainingQty());
            assertNotNull(after.getUpdatedAt(), "Sửa xong phải ghi mốc thời gian");
        }

        @Test
        @DisplayName("Làm dư thì còn thiếu ra số âm, không kẹp về 0")
        void overProductionShowsNegativeRemainder() {
            LocalDate day = dayOffset(3);
            PrepTask task = prepService.plan(anyOrderableProductId(), day, 10, null, userId(KITCHEN_1));

            prepService.update(task.getPrepTaskId(), 10, 14, null, userId(KITCHEN_1));

            assertEquals(-4, prepService.findById(task.getPrepTaskId()).getRemainingQty(),
                    "Làm dư cũng là chuyện cần nhìn thấy khi đặt số cho ca sau");
        }

        @Test
        @DisplayName("Thu hồi giữ lại bản ghi và biến nó khỏi kế hoạch trong ngày")
        void cancelKeepsRowButHidesIt() {
            LocalDate day = dayOffset(4);
            PrepTask task = prepService.plan(anyOrderableProductId(), day, 12, null, userId(KITCHEN_1));

            prepService.cancel(task.getPrepTaskId(), userId(KITCHEN_1));

            assertEquals("CANCELLED", prepService.findById(task.getPrepTaskId()).getStatus(),
                    "Bản ghi phải còn, vì nhật ký thao tác đã trỏ về mã này");
            assertTrue(prepService.planOf(day).stream()
                            .noneMatch(t -> t.getPrepTaskId() == task.getPrepTaskId()),
                    "Dòng đã thu hồi không được nằm trong kế hoạch của ngày nữa");
        }
    }

    @Nested
    @DisplayName("Ba chốt chặn")
    class Guards {

        @Test
        @DisplayName("Một món chỉ có một dòng kế hoạch trong một ngày")
        void oneRowPerProductPerDay() {
            LocalDate day = dayOffset(5);
            int productId = anyOrderableProductId();
            prepService.plan(productId, day, 10, null, userId(KITCHEN_1));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> prepService.plan(productId, day, 5, null, userId(KITCHEN_2)));

            assertTrue(e.getMessage().contains("đã có dòng kế hoạch"),
                    "Phải nói rõ là đã có dòng rồi, không phải một lỗi hệ thống. Nhận được: "
                            + e.getMessage());
        }

        @Test
        @DisplayName("Thu hồi rồi thì lập lại được cho đúng món trong đúng ngày")
        void cancelledRowDoesNotBlockReplanning() {
            LocalDate day = dayOffset(6);
            int productId = anyOrderableProductId();
            PrepTask first = prepService.plan(productId, day, 10, null, userId(KITCHEN_1));
            prepService.cancel(first.getPrepTaskId(), userId(KITCHEN_1));

            PrepTask second = prepService.plan(productId, day, 15, null, userId(KITCHEN_1));

            assertTrue(second.getPrepTaskId() > 0,
                    "Một lần bấm nhầm không được khoá món đó tới hết ngày");
        }

        @Test
        @DisplayName("Dòng đã chốt thì không sửa và không thu hồi được nữa")
        void closedRowIsFrozen() {
            LocalDate day = dayOffset(7);
            PrepTask task = prepService.plan(anyOrderableProductId(), day, 10, null, userId(KITCHEN_1));
            prepService.markDone(task.getPrepTaskId(), userId(KITCHEN_1));

            int id = task.getPrepTaskId();
            assertThrows(BusinessException.class,
                    () -> prepService.update(id, 20, 5, null, userId(KITCHEN_1)));
            assertThrows(BusinessException.class,
                    () -> prepService.cancel(id, userId(KITCHEN_1)));
            assertEquals("DONE", prepService.findById(id).getStatus());
        }

        @Test
        @DisplayName("Chỉ người lập mới thu hồi được")
        void onlyAuthorCanCancel() {
            LocalDate day = dayOffset(8);
            PrepTask task = prepService.plan(anyOrderableProductId(), day, 10, null, userId(KITCHEN_1));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> prepService.cancel(task.getPrepTaskId(), userId(KITCHEN_2)));

            assertTrue(e.getMessage().contains("người đã lập"), "Nhận được: " + e.getMessage());
            assertEquals("PLANNED", prepService.findById(task.getPrepTaskId()).getStatus(),
                    "Bị từ chối thì trạng thái phải nguyên vẹn");
        }
    }

    @Nested
    @DisplayName("Dữ liệu vào không hợp lệ")
    class Validation {

        @Test
        @DisplayName("Số lượng phải nằm trong khoảng cho phép")
        void quantityMustBeSane() {
            LocalDate day = dayOffset(9);
            int productId = anyOrderableProductId();

            assertThrows(ValidationException.class,
                    () -> prepService.plan(productId, day, 0, null, userId(KITCHEN_1)));
            assertThrows(ValidationException.class,
                    () -> prepService.plan(productId, day, 1000, null, userId(KITCHEN_1)));
        }

        @Test
        @DisplayName("Không lập kế hoạch cho ngày đã qua")
        void cannotPlanForThePast() {
            assertThrows(ValidationException.class,
                    () -> prepService.plan(anyOrderableProductId(), dayOffset(-1), 10, null,
                            userId(KITCHEN_1)));
        }

        @Test
        @DisplayName("Món không tồn tại thì báo không tìm thấy")
        void unknownProductIsRejected() {
            assertThrows(NotFoundException.class,
                    () -> prepService.plan(999_999, dayOffset(10), 10, null, userId(KITCHEN_1)));
        }
    }

    @Test
    @DisplayName("Kế hoạch của ngày chỉ trả về đúng ngày đó")
    void planOfDayIsScopedToThatDay() {
        LocalDate day = dayOffset(11);
        LocalDate other = dayOffset(12);
        int productId = anyOrderableProductId();
        prepService.plan(productId, day, 10, null, userId(KITCHEN_1));
        prepService.plan(productId, other, 20, null, userId(KITCHEN_1));

        List<PrepTask> plan = prepService.planOf(day);

        assertTrue(plan.stream().allMatch(t -> day.equals(t.getPrepDate())),
                "Lọt dòng của ngày khác thì bếp chuẩn bị nhầm số lượng");
        assertTrue(plan.stream().anyMatch(t -> t.getProductId() == productId));
    }
}
