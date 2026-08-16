package com.fastfood.flow;

import com.fastfood.config.DBContext;
import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.Notification;
import com.fastfood.service.shared.NotificationService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hộp thông báo của khách.
 * <p>
 * Bản chạy thử gửi tin qua kênh giả lập, nghĩa là không có bức thư nào thật sự tới tay khách.
 * Màn hình này vì thế là <b>nơi duy nhất</b> khách đọc được tin đơn bị huỷ hay tiền được hoàn,
 * nên phần đáng kiểm nhất không phải là danh sách hiện ra, mà là dấu đã đọc: sai chỗ đó thì
 * huy hiệu trên thanh điều hướng nói dối, và một huy hiệu nói dối thì lần sau không ai bấm.
 * <p>
 * Các bài đổi dấu đã đọc đều chạy trong {@link #giuNguyenHopThu}: dữ liệu mẫu là của chung cả
 * lượt chạy, và một bài để lại hộp thư đọc sạch sẽ làm bài kiểm dữ liệu mẫu ở dưới báo đỏ vì
 * một lý do chẳng liên quan gì tới mã nguồn.
 */
@DisplayName("Hộp thông báo của khách")
class NotificationInboxIT extends IntegrationTestBase {

    private final NotificationService notificationService = new NotificationService();

    /** Chạy một đoạn có đổi dấu đã đọc, rồi trả hộp thư của khách về đúng như trước. */
    private void giuNguyenHopThu(int userId, Runnable doan) {
        Map<Integer, String> truoc = new LinkedHashMap<>();
        try (Connection con = DBContext.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT notification_id, CONVERT(VARCHAR(19), read_at, 120) AS moc " +
                     "FROM dbo.Notification WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    truoc.put(rs.getInt("notification_id"), rs.getString("moc"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Khong doc duoc hop thu truoc khi thu", e);
        }

        try {
            doan.run();
        } finally {
            truoc.forEach((id, moc) -> exec(
                    "UPDATE dbo.Notification SET read_at = ? WHERE notification_id = ?", moc, id));
        }
    }

    private List<Integer> tinChuaDoc(int userId) {
        List<Integer> ids = new ArrayList<>();
        try (Connection con = DBContext.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT notification_id FROM dbo.Notification " +
                     "WHERE user_id = ? AND read_at IS NULL")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Khong dem duoc tin chua doc", e);
        }
        return ids;
    }

    /** Một đơn của khách này có tin gắn kèm. */
    private int donCoTin(int userId) {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 order_id FROM dbo.Notification WHERE user_id = ? " +
                "ORDER BY notification_id DESC", userId);
        if (id == null) {
            throw new IllegalStateException("Du lieu mau khong co tin nao cho user " + userId);
        }
        return id;
    }

    @Nested
    @DisplayName("Dữ liệu mẫu")
    class Seed {

        @Test
        @DisplayName("Mỗi khách mẫu đều có tin để hộp thông báo không mở ra trống")
        void seedHasNotifications() {
            Page<Notification> trang = notificationService.pageOfUser(userId(CUSTOMER_1), 1);

            assertFalse(trang.isEmptyPage(),
                    "Không có tin nào thì màn hình Thông báo chỉ trình bày được đúng khối rỗng");
            assertTrue(trang.getItems().stream().allMatch(n -> n.getContent() != null),
                    "Nội dung rỗng thì dòng tin chẳng nói được gì — tiêu đề loại tin là chưa đủ");
        }

        /**
         * Kiểm bằng "có ít nhất một" ở cả hai phía chứ không phải "không có dòng nào vi phạm":
         * các bài test khác trong cùng lượt chạy có huỷ đơn thật, và mỗi lần như vậy lại sinh
         * thêm một tin chưa đọc thuộc về một đơn đã khép lại. Ràng buộc chặt hơn sẽ đỏ vì việc
         * một bài test khác vừa làm, chứ không phải vì dữ liệu mẫu sai.
         */
        @Test
        @DisplayName("Dữ liệu mẫu có cả tin đã đọc lẫn tin chưa đọc")
        void seedSplitsReadFromUnread() {
            assertTrue(count("SELECT COUNT(*) FROM dbo.Notification WHERE read_at IS NULL") > 0,
                    "Đọc hết sạch thì huy hiệu trên thanh điều hướng không bao giờ hiện, và cả "
                            + "nút \"đánh dấu đã đọc hết\" cũng không có gì để làm");
            assertTrue(count(
                    "SELECT COUNT(*) FROM dbo.Notification n " +
                    "JOIN dbo.Orders o ON o.order_id = n.order_id " +
                    "WHERE n.read_at IS NOT NULL " +
                    "  AND o.order_status IN ('COMPLETED','CANCELLED','EXPIRED')") > 0,
                    "Tin của đơn đã khép lại phải được đánh dấu đã đọc sẵn — đọc tất cả thành "
                            + "chưa đọc thì huy hiệu đếm cả chuyện xảy ra từ đời nào");
        }

        @Test
        @DisplayName("Tin gửi cho khách này không lẫn sang khách kia")
        void inboxesAreSeparate() {
            int khach = userId(CUSTOMER_1);

            List<Notification> cua_khach = notificationService.pageOfUser(khach, 1).getItems();

            assertTrue(cua_khach.stream()
                            .allMatch(n -> n.getUserId() != null && n.getUserId() == khach),
                    "Hộp thông báo lọc theo user_id ngay trong câu truy vấn, nên không được "
                            + "lọt tin của tài khoản khác");
        }
    }

    @Nested
    @DisplayName("Dấu đã đọc")
    class ReadMark {

        @Test
        @DisplayName("Đánh dấu đã đọc hết thì số chưa đọc về 0")
        void markAllReadClearsBadge() {
            int khach = userId(CUSTOMER_1);
            giuNguyenHopThu(khach, () -> {
                exec("UPDATE dbo.Notification SET read_at = NULL WHERE user_id = ?", khach);
                assertTrue(notificationService.unreadCount(khach) > 0);

                notificationService.markAllRead(khach);

                assertEquals(0, notificationService.unreadCount(khach));
            });
        }

        @Test
        @DisplayName("Đánh dấu lần hai không dời mốc đã đọc của tin đọc từ trước")
        void markAllReadKeepsExistingTimestamps() {
            int khach = userId(CUSTOMER_1);
            giuNguyenHopThu(khach, () -> {
                int tin = tinChuaDoc(khach).isEmpty()
                        ? scalar(Integer.class, "SELECT TOP 1 notification_id FROM dbo.Notification "
                                 + "WHERE user_id = ? ORDER BY notification_id", khach)
                        : tinChuaDoc(khach).get(0);
                exec("UPDATE dbo.Notification SET read_at = '2020-01-01 08:00:00' " +
                     "WHERE notification_id = ?", tin);

                notificationService.markAllRead(khach);

                assertEquals("2020-01-01 08:00:00",
                        text("SELECT CONVERT(VARCHAR(19), read_at, 120) FROM dbo.Notification " +
                             "WHERE notification_id = ?", tin),
                        "Điều kiện read_at IS NULL nằm trong chính câu UPDATE — thiếu nó thì cột "
                                + "này không còn nói được tin nào đọc lúc nào");
            });
        }

        @Test
        @DisplayName("Mở trang theo dõi đơn là đọc tin của riêng đơn đó")
        void trackingPageMarksThatOrder() {
            int khach = userId(CUSTOMER_1);
            giuNguyenHopThu(khach, () -> {
                exec("UPDATE dbo.Notification SET read_at = NULL WHERE user_id = ?", khach);
                int don = donCoTin(khach);
                int truoc = notificationService.unreadCount(khach);
                int cua_don = count("SELECT COUNT(*) FROM dbo.Notification " +
                        "WHERE user_id = ? AND order_id = ?", khach, don);

                notificationService.markReadByOrder(khach, don);

                assertEquals(0, count("SELECT COUNT(*) FROM dbo.Notification " +
                        "WHERE user_id = ? AND order_id = ? AND read_at IS NULL", khach, don));
                assertEquals(truoc - cua_don, notificationService.unreadCount(khach),
                        "Chỉ tin của đơn vừa mở rời khỏi con số chưa đọc, không phải cả hộp");
            });
        }

        @Test
        @DisplayName("Không xoá được dấu chưa đọc của người khác")
        void cannotMarkAnotherCustomerInbox() {
            int chu = userId(CUSTOMER_1);
            giuNguyenHopThu(chu, () -> {
                exec("UPDATE dbo.Notification SET read_at = NULL WHERE user_id = ?", chu);
                int don = donCoTin(chu);
                int nguoi_la = userId(CUSTOMER_2);

                notificationService.markReadByOrder(nguoi_la, don);

                assertTrue(count("SELECT COUNT(*) FROM dbo.Notification " +
                                "WHERE user_id = ? AND order_id = ? AND read_at IS NULL", chu, don) > 0,
                        "Điều kiện user_id trong câu UPDATE là thứ giữ cho một khách mở đơn của "
                                + "người khác cũng không xoá được dấu chưa đọc của họ");
            });
        }
    }

    @Nested
    @DisplayName("Phân trang")
    class Paging {

        @Test
        @DisplayName("Tổng số đếm theo toàn bộ hộp, không theo số dòng của một trang")
        void totalCountsWholeInbox() {
            int khach = userId(CUSTOMER_1);
            int that_su = count("SELECT COUNT(*) FROM dbo.Notification WHERE user_id = ?", khach);

            Page<Notification> trang = notificationService.pageOfUser(khach, 1);

            assertEquals(that_su, trang.getTotalItems(),
                    "Dòng \"đang xem 1–20 trong N\" chỉ đúng khi N là tổng thật");
            assertTrue(trang.getItems().size() <= Page.SIZE);
        }

        @Test
        @DisplayName("Tin mới nhất nằm trên cùng")
        void newestFirst() {
            List<Notification> tin = notificationService.pageOfUser(userId(CUSTOMER_1), 1).getItems();

            for (int i = 1; i < tin.size(); i++) {
                assertTrue(tin.get(i - 1).getNotificationId() > tin.get(i).getNotificationId(),
                        "Hộp thông báo đọc từ trên xuống, nên chuyện vừa xảy ra phải ở trên cùng");
            }
        }

        @Test
        @DisplayName("Số trang lạ trên địa chỉ không làm hỏng truy vấn")
        void oddPageNumberIsClamped() {
            Page<Notification> trang = notificationService.pageOfUser(userId(CUSTOMER_1), -3);

            assertEquals(1, trang.getPageNo(),
                    "Số trang tới từ địa chỉ người dùng gõ được, nên phải ép về khoảng hợp lệ "
                            + "trước khi đi vào OFFSET");
        }
    }
}
