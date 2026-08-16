package com.fastfood.flow;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.entity.PosHold;
import com.fastfood.service.staff.PosHoldService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phiếu treo tại quầy — màn hình bán hàng của thu ngân.
 * <p>
 * Dùng {@code cashier2} cho phần lớn bài test vì dữ liệu mẫu đã treo sẵn hai phiếu cho
 * {@code cashier1}: đếm tuyệt đối trên người đó sẽ hỏng ngay khi ai đó sửa dữ liệu mẫu.
 */
@DisplayName("Phiếu treo tại quầy")
class PosHoldIT extends IntegrationTestBase {

    private final PosHoldService holdService = new PosHoldService();

    private Map<Integer, Integer> gio(int productId, int quantity) {
        Map<Integer, Integer> lines = new LinkedHashMap<>();
        lines.put(productId, quantity);
        return lines;
    }

    /** Tên phiếu phải khác nhau giữa các bài test vì UQ_PosHold_cashier_label chặn trùng. */
    private PosHold treo(int cashierId, String label, Map<Integer, Integer> lines) {
        return holdService.hold(cashierId, label, null, lines);
    }

    @Nested
    @DisplayName("Vòng đời một phiếu")
    class Crud {

        @Test
        @DisplayName("Treo, đổi tên, sửa số lượng, rồi bỏ phiếu")
        void fullCycle() {
            int cashier = userId(CASHIER_2);
            int product = anyOrderableProductId();

            PosHold hold = treo(cashier, "Bàn 1 — vòng đời", gio(product, 2));
            int id = hold.getHoldId();
            assertEquals(1, hold.getLineCount());
            assertEquals(2, hold.getTotalQuantity());

            holdService.rename(id, cashier, "Bàn 1 — đã đổi tên", "khách đi rút tiền");
            PosHold after = holdService.findOwn(id, cashier);
            assertEquals("Bàn 1 — đã đổi tên", after.getLabel());
            assertEquals("khách đi rút tiền", after.getNote());
            assertNotNull(after.getUpdatedAt(), "Sửa xong phải đánh dấu thời điểm sửa");

            holdService.setQuantity(id, cashier, product, 5);
            assertEquals(5, holdService.findOwn(id, cashier).getTotalQuantity());

            holdService.discard(id, cashier);
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.PosHold WHERE hold_id = ?", id));
        }

        @Test
        @DisplayName("Bỏ phiếu thì dòng món đi theo, không để lại dòng mồ côi")
        void discardCascadesToItems() {
            int cashier = userId(CASHIER_2);
            PosHold hold = treo(cashier, "Bàn 2 — kiểm cascade", gio(anyOrderableProductId(), 3));
            int id = hold.getHoldId();
            assertEquals(1, count("SELECT COUNT(*) FROM dbo.PosHoldItem WHERE hold_id = ?", id));

            holdService.discard(id, cashier);

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.PosHoldItem WHERE hold_id = ?", id));
        }

        @Test
        @DisplayName("Lấy phiếu ra là xoá phiếu — không bán được hai lần")
        void resumeRemovesHold() {
            int cashier = userId(CASHIER_2);
            int product = anyOrderableProductId();
            PosHold hold = treo(cashier, "Bàn 3 — lấy ra", gio(product, 4));
            int id = hold.getHoldId();

            Map<Integer, Integer> lines = holdService.resume(id, cashier);

            assertEquals(1, lines.size());
            assertEquals(4, lines.get(product));
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.PosHold WHERE hold_id = ?", id),
                    "Giữ phiếu lại thì ca sau mở màn hình vẫn thấy nó và tưởng chưa ai thu tiền");
            assertThrows(NotFoundException.class, () -> holdService.resume(id, cashier),
                    "Lấy ra lần thứ hai phải báo không tìm thấy");
        }

        @Test
        @DisplayName("Bỏ nốt dòng cuối cùng thì phiếu tự biến mất")
        void emptyingRemovesHold() {
            int cashier = userId(CASHIER_2);
            int product = anyOrderableProductId();
            PosHold hold = treo(cashier, "Bàn 4 — dọn sạch", gio(product, 1));
            int id = hold.getHoldId();

            holdService.setQuantity(id, cashier, product, 0);

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.PosHold WHERE hold_id = ?", id),
                    "Phiếu không còn món nào chỉ là một cái tên chiếm chỗ");
        }

        @Test
        @DisplayName("Treo thêm cùng một món thì cộng dồn, không thành hai dòng")
        void addingSameProductMerges() {
            int cashier = userId(CASHIER_2);
            int product = anyOrderableProductId();
            PosHold hold = treo(cashier, "Bàn 5 — cộng dồn", gio(product, 2));
            int id = hold.getHoldId();

            holdService.addItem(id, cashier, product, 3);

            PosHold after = holdService.findOwn(id, cashier);
            assertEquals(1, after.getLineCount(), "UQ_PosHoldItem không cho hai dòng cùng món");
            assertEquals(5, after.getTotalQuantity());
        }

        @Test
        @DisplayName("Danh sách phiếu của mỗi thu ngân tách bạch nhau")
        void listIsPerCashier() {
            int cashier2 = userId(CASHIER_2);
            int truoc = holdService.myHolds(cashier2).size();
            treo(cashier2, "Bàn 6 — riêng tôi", gio(anyOrderableProductId(), 1));

            assertEquals(truoc + 1, holdService.myHolds(cashier2).size());
            assertTrue(holdService.myHolds(userId(CASHIER_1)).stream()
                            .noneMatch(h -> "Bàn 6 — riêng tôi".equals(h.getLabel())),
                    "Phiếu của người này không được lọt sang danh sách của người kia");
        }
    }

    @Nested
    @DisplayName("Chốt chặn")
    class Guards {

        @Test
        @DisplayName("Phải đặt tên cho phiếu, và giỏ trống thì không có gì để treo")
        void labelAndLinesAreRequired() {
            int cashier = userId(CASHIER_2);
            int product = anyOrderableProductId();

            assertThrows(ValidationException.class,
                    () -> holdService.hold(cashier, "   ", null, gio(product, 1)));
            assertThrows(ValidationException.class,
                    () -> holdService.hold(cashier, "Giỏ rỗng", null, new LinkedHashMap<>()));
        }

        @Test
        @DisplayName("Hai phiếu cùng tên trong tay một người thì chính họ không phân biệt được")
        void duplicateLabelIsRejected() {
            int cashier = userId(CASHIER_2);
            int product = anyOrderableProductId();
            treo(cashier, "Bàn 7 — trùng tên", gio(product, 1));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> treo(cashier, "Bàn 7 — trùng tên", gio(product, 1)));

            assertTrue(e.getMessage().contains("đặt tên khác"), "Nhận được: " + e.getMessage());
        }

        @Test
        @DisplayName("Cùng tên nhưng khác thu ngân thì vẫn treo được")
        void sameLabelDifferentCashierIsFine() {
            int product = anyOrderableProductId();
            treo(userId(CASHIER_2), "Bàn 8 — tên chung", gio(product, 1));
            PosHold cua_nguoi_khac = treo(userId(CASHIER_1), "Bàn 8 — tên chung", gio(product, 1));

            assertNotNull(cua_nguoi_khac);
            assertEquals(2, count("SELECT COUNT(*) FROM dbo.PosHold WHERE label = ?",
                    "Bàn 8 — tên chung"));
        }

        @Test
        @DisplayName("Không đụng được vào phiếu của thu ngân khác")
        void cannotTouchAnotherCashierHold() {
            int chu = userId(CASHIER_2);
            int nguoi_la = userId(CASHIER_1);
            int product = anyOrderableProductId();
            int id = treo(chu, "Bàn 9 — của tôi", gio(product, 1)).getHoldId();

            assertThrows(BusinessException.class, () -> holdService.findOwn(id, nguoi_la));
            assertThrows(BusinessException.class, () -> holdService.resume(id, nguoi_la));
            assertThrows(BusinessException.class, () -> holdService.discard(id, nguoi_la));
            assertThrows(BusinessException.class,
                    () -> holdService.rename(id, nguoi_la, "cướp phiếu", null));

            assertEquals(1, count("SELECT COUNT(*) FROM dbo.PosHold WHERE hold_id = ?", id),
                    "Phiếu phải còn nguyên sau bốn lần bị từ chối");
        }

        @Test
        @DisplayName("Số lượng vô lý và món không tồn tại đều bị chặn")
        void insaneQuantityAndMissingProduct() {
            int cashier = userId(CASHIER_2);

            assertThrows(ValidationException.class,
                    () -> holdService.hold(cashier, "Bàn 10 — số lượng", null,
                            gio(anyOrderableProductId(), 999)));
            assertThrows(NotFoundException.class,
                    () -> holdService.hold(cashier, "Bàn 10 — món ma", null, gio(999_999, 1)));

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.PosHold WHERE label LIKE ?", "Bàn 10%"),
                    "Hỏng giữa chừng thì cả phiếu phải cuộn lại, không để phần đầu nằm lại");
        }
    }

    @Nested
    @DisplayName("Món và giá đọc mới mỗi lần mở phiếu")
    class LivePricing {

        @Test
        @DisplayName("Món hết hàng vẫn treo được, nhưng phiếu bị đánh dấu để cảnh báo")
        void unavailableItemIsFlaggedNotBlocked() {
            int cashier = userId(CASHIER_2);
            int het_hang = unavailableProductId();

            PosHold hold = treo(cashier, "Bàn 11 — món hết", gio(het_hang, 1));

            assertTrue(hold.isAnyUnavailable(),
                    "Treo lúc còn hàng, lấy ra lúc đã hết — chặn ở đây chỉ làm thu ngân "
                            + "không cất nổi giỏ đang dở, nên chỉ cảnh báo");
            assertFalse(hold.getItems().get(0).isOrderable());
        }

        @Test
        @DisplayName("Đổi giá món thì phiếu treo hiện giá mới, không giữ giá lúc treo")
        void priceIsReadLive() {
            int cashier = userId(CASHIER_2);
            int product = anyOrderableProductId();
            BigDecimal gia_cu = money("SELECT price FROM dbo.Product WHERE product_id = ?", product);
            int id = treo(cashier, "Bàn 12 — đổi giá", gio(product, 2)).getHoldId();
            assertEquals(0, gia_cu.multiply(BigDecimal.valueOf(2))
                    .compareTo(holdService.findOwn(id, cashier).getTotal()));

            try {
                exec("UPDATE dbo.Product SET price = ? WHERE product_id = ?",
                        gia_cu.add(BigDecimal.valueOf(1000)), product);

                BigDecimal moi = holdService.findOwn(id, cashier).getTotal();

                assertEquals(0, gia_cu.add(BigDecimal.valueOf(1000)).multiply(BigDecimal.valueOf(2))
                                .compareTo(moi),
                        "Bảng PosHoldItem cố ý không lưu giá — giá phải đọc mới từ bảng món");
            } finally {
                // Trả giá về như cũ: các bài test khác trong cùng lượt chạy dùng chung dữ liệu mẫu.
                exec("UPDATE dbo.Product SET price = ? WHERE product_id = ?", gia_cu, product);
            }
        }

        @Test
        @DisplayName("Dữ liệu mẫu có sẵn hai phiếu treo để màn hình không mở ra trống")
        void seedHasHolds() {
            List<PosHold> cua_thu_ngan_1 = holdService.myHolds(userId(CASHIER_1));

            assertTrue(cua_thu_ngan_1.size() >= 2, "Nhận được: " + cua_thu_ngan_1.size());
            assertTrue(cua_thu_ngan_1.stream().anyMatch(h -> h.getTotal().signum() > 0),
                    "Phiếu mẫu phải có món và ra được thành tiền");
        }
    }
}
