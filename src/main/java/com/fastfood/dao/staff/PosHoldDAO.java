package com.fastfood.dao.staff;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.PosHold;
import com.fastfood.model.entity.PosHoldItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Truy vấn hai bảng PosHold và PosHoldItem — phiếu treo tại quầy. */
public class PosHoldDAO {

    private static final String BASE =
            "SELECT h.hold_id, h.cashier_id, h.label, h.note, h.created_at, h.updated_at " +
            "FROM dbo.PosHold h ";

    /** Dòng món kèm tên, giá và tình trạng bán — ba thứ đọc mới, không lưu trong bảng. */
    private static final String ITEM_BASE =
            "SELECT i.hold_item_id, i.hold_id, i.product_id, i.quantity, " +
            "       p.name AS product_name, p.price, p.is_available, p.status AS product_status " +
            "FROM dbo.PosHoldItem i " +
            "JOIN dbo.Product p ON p.product_id = i.product_id ";

    // ============================================================ phiếu

    public int insert(Connection con, PosHold hold) throws SQLException {
        String sql = "INSERT INTO dbo.PosHold (cashier_id, label, note, created_at) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, hold.getCashierId());
            JdbcSupport.setString(ps, 2, hold.getLabel());
            JdbcSupport.setString(ps, 3, hold.getNote());
            JdbcSupport.setDateTime(ps, 4, hold.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    hold.setHoldId(keys.getInt(1));
                }
            }
        }
        return hold.getHoldId();
    }

    /** Phiếu kèm đủ dòng món. Trả về null nếu không có phiếu nào mang mã đó. */
    public PosHold findById(Connection con, int holdId) throws SQLException {
        PosHold hold;
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE h.hold_id = ?")) {
            ps.setInt(1, holdId);
            List<PosHold> list = collect(ps);
            if (list.isEmpty()) {
                return null;
            }
            hold = list.get(0);
        }
        hold.setItems(findItems(con, holdId));
        return hold;
    }

    /**
     * Phiếu treo của một thu ngân, mới nhất trước, kèm đủ dòng món.
     * <p>
     * Món lấy trong <b>một</b> lượt truy vấn cho cả danh sách rồi mới gom theo phiếu. Hỏi từng
     * phiếu một thì mỗi lần mở màn bán hàng lại thành mấy lượt đi lại chỉ để dựng một khối phụ.
     */
    public List<PosHold> findByCashier(Connection con, int cashierId) throws SQLException {
        List<PosHold> holds;
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE h.cashier_id = ? ORDER BY h.created_at DESC, h.hold_id DESC")) {
            ps.setInt(1, cashierId);
            holds = collect(ps);
        }
        if (holds.isEmpty()) {
            return holds;
        }

        Map<Integer, List<PosHoldItem>> byHold = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement(
                ITEM_BASE + "JOIN dbo.PosHold h ON h.hold_id = i.hold_id " +
                            "WHERE h.cashier_id = ? ORDER BY i.hold_item_id")) {
            ps.setInt(1, cashierId);
            for (PosHoldItem item : collectItems(ps)) {
                byHold.computeIfAbsent(item.getHoldId(), k -> new ArrayList<>()).add(item);
            }
        }
        for (PosHold hold : holds) {
            hold.setItems(byHold.get(hold.getHoldId()));
        }
        return holds;
    }

    public int countByCashier(Connection con, int cashierId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.PosHold WHERE cashier_id = ?")) {
            ps.setInt(1, cashierId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int updateHeader(Connection con, int holdId, int cashierId, String label, String note,
                            LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.PosHold SET label = ?, note = ?, updated_at = ? " +
                     "WHERE hold_id = ? AND cashier_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setString(ps, 1, label);
            JdbcSupport.setString(ps, 2, note);
            JdbcSupport.setDateTime(ps, 3, now);
            ps.setInt(4, holdId);
            ps.setInt(5, cashierId);
            return ps.executeUpdate();
        }
    }

    /** Chỉ đánh dấu vừa sửa. Tách khỏi {@link #updateHeader} để việc sửa dòng món không phải
     *  ghi đè lại tên phiếu — ghi đè bằng null sẽ vi phạm CK_PosHold_label. */
    public int touch(Connection con, int holdId, LocalDateTime now) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.PosHold SET updated_at = ? WHERE hold_id = ?")) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, holdId);
            return ps.executeUpdate();
        }
    }

    /**
     * Xoá hẳn phiếu. Dòng món tự đi theo nhờ {@code ON DELETE CASCADE} — phiếu treo là bản
     * nháp, không phải dữ liệu giao dịch cần giữ vết.
     */
    public int delete(Connection con, int holdId, int cashierId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.PosHold WHERE hold_id = ? AND cashier_id = ?")) {
            ps.setInt(1, holdId);
            ps.setInt(2, cashierId);
            return ps.executeUpdate();
        }
    }

    // ============================================================ dòng món

    public List<PosHoldItem> findItems(Connection con, int holdId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                ITEM_BASE + "WHERE i.hold_id = ? ORDER BY i.hold_item_id")) {
            ps.setInt(1, holdId);
            return collectItems(ps);
        }
    }

    /**
     * Thêm món vào phiếu; đã có thì cộng dồn số lượng.
     * <p>
     * Gộp cả hai trường hợp vào một câu {@code MERGE} thay vì đọc trước rồi quyết định: giữa hai
     * bước đó một tab khác của cùng thu ngân có thể vừa thêm đúng món ấy, và ràng buộc
     * {@code UQ_PosHoldItem} sẽ chặn lượt ghi sau bằng một lỗi khó hiểu.
     */
    public void addItem(Connection con, int holdId, int productId, int quantity) throws SQLException {
        String sql =
            "MERGE dbo.PosHoldItem AS dich " +
            "USING (SELECT ? AS hold_id, ? AS product_id, ? AS quantity) AS nguon " +
            "   ON dich.hold_id = nguon.hold_id AND dich.product_id = nguon.product_id " +
            "WHEN MATCHED THEN UPDATE SET dich.quantity = dich.quantity + nguon.quantity " +
            "WHEN NOT MATCHED THEN INSERT (hold_id, product_id, quantity) " +
            "     VALUES (nguon.hold_id, nguon.product_id, nguon.quantity);";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, holdId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
    }

    public int updateItemQuantity(Connection con, int holdId, int productId, int quantity)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.PosHoldItem SET quantity = ? WHERE hold_id = ? AND product_id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, holdId);
            ps.setInt(3, productId);
            return ps.executeUpdate();
        }
    }

    public int removeItem(Connection con, int holdId, int productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.PosHoldItem WHERE hold_id = ? AND product_id = ?")) {
            ps.setInt(1, holdId);
            ps.setInt(2, productId);
            return ps.executeUpdate();
        }
    }

    // ============================================================ dựng đối tượng

    private List<PosHold> collect(PreparedStatement ps) throws SQLException {
        List<PosHold> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PosHold h = new PosHold();
                h.setHoldId(rs.getInt("hold_id"));
                h.setCashierId(rs.getInt("cashier_id"));
                h.setLabel(rs.getNString("label"));
                h.setNote(rs.getNString("note"));
                h.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                h.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
                list.add(h);
            }
        }
        return list;
    }

    private List<PosHoldItem> collectItems(PreparedStatement ps) throws SQLException {
        List<PosHoldItem> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PosHoldItem i = new PosHoldItem();
                i.setHoldItemId(rs.getInt("hold_item_id"));
                i.setHoldId(rs.getInt("hold_id"));
                i.setProductId(rs.getInt("product_id"));
                i.setQuantity(rs.getInt("quantity"));
                i.setProductName(rs.getNString("product_name"));
                i.setUnitPrice(rs.getBigDecimal("price"));
                i.setAvailable(rs.getBoolean("is_available"));
                i.setProductStatus(rs.getString("product_status"));
                list.add(i);
            }
        }
        return list;
    }
}
