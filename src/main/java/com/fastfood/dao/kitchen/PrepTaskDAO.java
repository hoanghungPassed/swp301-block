package com.fastfood.dao.kitchen;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.PrepTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng PrepTask — kế hoạch chuẩn bị sẵn của bếp. */
public class PrepTaskDAO {

    private static final String BASE =
            "SELECT pt.prep_task_id, pt.product_id, pt.prep_date, pt.planned_qty, pt.done_qty, " +
            "       pt.note, pt.created_by, pt.created_at, pt.updated_at, pt.status, " +
            "       p.name AS product_name, u.full_name AS created_by_name " +
            "FROM dbo.PrepTask pt " +
            "JOIN dbo.Product p ON p.product_id = pt.product_id " +
            "JOIN dbo.Users u   ON u.user_id    = pt.created_by ";

    /**
     * Lập một dòng kế hoạch. Ném lỗi trùng khoá khi món đó đã có kế hoạch trong ngày —
     * xem {@code UX_PrepTask_date_product}. Tầng Service bắt lỗi đó và đổi thành thông báo
     * đọc được thay vì chặn bằng một lượt đọc trước, vì giữa đọc và ghi người khác có thể
     * vừa lập xong dòng cho đúng món ấy.
     */
    public int insert(Connection con, PrepTask task) throws SQLException {
        String sql = "INSERT INTO dbo.PrepTask (product_id, prep_date, planned_qty, done_qty, " +
                     "note, created_by, created_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, task.getProductId());
            JdbcSupport.setDate(ps, 2, task.getPrepDate());
            ps.setInt(3, task.getPlannedQty());
            ps.setInt(4, task.getDoneQty());
            JdbcSupport.setString(ps, 5, task.getNote());
            ps.setInt(6, task.getCreatedBy());
            JdbcSupport.setDateTime(ps, 7, task.getCreatedAt());
            ps.setString(8, task.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    task.setPrepTaskId(keys.getInt(1));
                }
            }
        }
        return task.getPrepTaskId();
    }

    public PrepTask findById(Connection con, int prepTaskId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE pt.prep_task_id = ?")) {
            ps.setInt(1, prepTaskId);
            List<PrepTask> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /** Kế hoạch của một ngày, món còn thiếu nhiều xếp lên trước để bếp biết làm gì tiếp theo. */
    public List<PrepTask> findByDate(Connection con, LocalDate date) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE +
                "WHERE pt.prep_date = ? AND pt.status <> 'CANCELLED' " +
                "ORDER BY (pt.planned_qty - pt.done_qty) DESC, p.name")) {
            JdbcSupport.setDate(ps, 1, date);
            return collect(ps);
        }
    }

    /**
     * Sửa số lượng dự kiến, số đã làm và ghi chú.
     * <p>
     * Điều kiện "còn đang lập" nằm ngay trong câu lệnh chứ không kiểm tra trước rồi mới ghi:
     * giữa hai bước đó người khác có thể vừa chốt hoặc vừa thu hồi dòng này.
     */
    public int update(Connection con, int prepTaskId, int plannedQty, int doneQty,
                      String note, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.PrepTask SET planned_qty = ?, done_qty = ?, note = ?, updated_at = ? " +
                     "WHERE prep_task_id = ? AND status = 'PLANNED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, plannedQty);
            ps.setInt(2, doneQty);
            JdbcSupport.setString(ps, 3, note);
            JdbcSupport.setDateTime(ps, 4, now);
            ps.setInt(5, prepTaskId);
            return ps.executeUpdate();
        }
    }

    /** Chốt kế hoạch đã làm xong. Chốt rồi thì không sửa được nữa — xem {@link #update}. */
    public int markDone(Connection con, int prepTaskId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.PrepTask SET status = 'DONE', updated_at = ? " +
                     "WHERE prep_task_id = ? AND status = 'PLANNED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, prepTaskId);
            return ps.executeUpdate();
        }
    }

    /**
     * Thu hồi dòng lập nhầm. Chỉ người lập mới thu hồi được — cùng quy tắc với sự cố bếp,
     * và điều kiện đó nằm trong chính câu lệnh.
     */
    public int cancel(Connection con, int prepTaskId, int userId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.PrepTask SET status = 'CANCELLED', updated_at = ? " +
                     "WHERE prep_task_id = ? AND status = 'PLANNED' AND created_by = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, prepTaskId);
            ps.setInt(3, userId);
            return ps.executeUpdate();
        }
    }

    private List<PrepTask> collect(PreparedStatement ps) throws SQLException {
        List<PrepTask> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PrepTask t = new PrepTask();
                t.setPrepTaskId(rs.getInt("prep_task_id"));
                t.setProductId(rs.getInt("product_id"));
                t.setPrepDate(JdbcSupport.getDate(rs, "prep_date"));
                t.setPlannedQty(rs.getInt("planned_qty"));
                t.setDoneQty(rs.getInt("done_qty"));
                t.setNote(rs.getNString("note"));
                t.setCreatedBy(rs.getInt("created_by"));
                t.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                t.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
                t.setStatus(rs.getString("status"));
                t.setProductName(rs.getNString("product_name"));
                t.setCreatedByName(rs.getNString("created_by_name"));
                list.add(t);
            }
        }
        return list;
    }
}
