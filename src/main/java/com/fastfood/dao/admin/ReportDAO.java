package com.fastfood.dao.admin;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.model.dto.Dtos.DashboardKpi;
import com.fastfood.model.dto.Dtos.ReportRow;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

public class ReportDAO {

    public DashboardKpi loadKpi(Connection con, LocalDateTime from, LocalDateTime to, int overdueMinutes)
            throws SQLException {
        DashboardKpi kpi = new DashboardKpi();

        /* Tiền chỉ đi một chiều nên chỉ có một mốc để quét: paid_at. Không cần phân biệt
           thu và thuần như bản trước — hai con số ấy luôn bằng nhau. */
        String revenueSql =
                "SELECT ISNULL(SUM(p.amount), 0) AS net FROM dbo.Payment p " +
                "WHERE p.paid_at BETWEEN ? AND ?";
        try (PreparedStatement ps = con.prepareStatement(revenueSql)) {
            JdbcSupport.setDateTime(ps, 1, from);
            JdbcSupport.setDateTime(ps, 2, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setNetRevenue(rs.getBigDecimal("net"));
                }
            }
        }

        String countSql =
                "SELECT ISNULL(SUM(CASE WHEN order_source = 'ONLINE_PREORDER' THEN 1 ELSE 0 END), 0) AS online_cnt, " +
                "       ISNULL(SUM(CASE WHEN order_source = 'POS'             THEN 1 ELSE 0 END), 0) AS pos_cnt, " +
                "       ISNULL(SUM(CASE WHEN order_status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_cnt, " +
                "       ISNULL(SUM(CASE WHEN order_status = 'EXPIRED'   THEN 1 ELSE 0 END), 0) AS expired_cnt " +
                "FROM dbo.Orders WHERE created_at BETWEEN ? AND ?";
        try (PreparedStatement ps = con.prepareStatement(countSql)) {
            JdbcSupport.setDateTime(ps, 1, from);
            JdbcSupport.setDateTime(ps, 2, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setOnlineOrderCount(rs.getInt("online_cnt"));
                    kpi.setPosOrderCount(rs.getInt("pos_cnt"));
                    kpi.setCompletedOrderCount(rs.getInt("completed_cnt"));
                    kpi.setExpiredOrderCount(rs.getInt("expired_cnt"));
                }
            }
        }

        String onTimeSql =
                "SELECT COUNT(*) AS total, " +
                "       ISNULL(SUM(CASE WHEN ready_at <= pickup_time THEN 1 ELSE 0 END), 0) AS on_time, " +
                "       AVG(CAST(DATEDIFF(MINUTE, released_to_kds_at, ready_at) AS FLOAT)) AS avg_lead " +
                "FROM dbo.Orders " +
                "WHERE order_source = 'ONLINE_PREORDER' AND ready_at IS NOT NULL " +
                "  AND pickup_time BETWEEN ? AND ?";
        try (PreparedStatement ps = con.prepareStatement(onTimeSql)) {
            JdbcSupport.setDateTime(ps, 1, from);
            JdbcSupport.setDateTime(ps, 2, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setTotalReadyMeasured(rs.getInt("total"));
                    kpi.setOnTimeReadyCount(rs.getInt("on_time"));
                    double avg = rs.getDouble("avg_lead");
                    kpi.setAvgPrepLeadMinutes(rs.wasNull() ? null : avg);
                }
            }
        }

        String liveSql =
                "SELECT COUNT(*) AS ready_cnt, " +
                "       ISNULL(SUM(CASE WHEN order_source = 'ONLINE_PREORDER' " +
                "                        AND DATEADD(MINUTE, ?, pickup_time) < ? THEN 1 ELSE 0 END), 0) AS overdue_cnt " +
                "FROM dbo.Orders WHERE order_status = 'READY'";
        try (PreparedStatement ps = con.prepareStatement(liveSql)) {
            ps.setInt(1, overdueMinutes);
            JdbcSupport.setDateTime(ps, 2, DateTimeUtil.now());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setReadyOrderCount(rs.getInt("ready_cnt"));
                    kpi.setOverduePickupCount(rs.getInt("overdue_cnt"));
                }
            }
        }
        return kpi;
    }

    public List<ReportRow> bestSellers(Connection con, LocalDateTime from, LocalDateTime to, int limit)
            throws SQLException {
        String sql = "SELECT TOP (?) p.name AS product_name, c.name AS category_name, " +
                     "       SUM(oi.quantity) AS total_qty, SUM(oi.quantity * oi.unit_price) AS total_amount " +
                     "FROM dbo.OrderItem oi " +
                     "JOIN dbo.Orders   o ON o.order_id = oi.order_id " +
                     "JOIN dbo.Product  p ON p.product_id = oi.product_id " +
                     "JOIN dbo.Category c ON c.category_id = p.category_id " +
                     "WHERE o.order_status = 'COMPLETED' AND o.completed_at BETWEEN ? AND ? " +
                     "GROUP BY p.product_id, p.name, c.name " +
                     "ORDER BY SUM(oi.quantity) DESC";
        List<ReportRow> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            JdbcSupport.setDateTime(ps, 2, from);
            JdbcSupport.setDateTime(ps, 3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReportRow row = new ReportRow(rs.getNString("product_name"),
                            rs.getLong("total_qty"), rs.getBigDecimal("total_amount"));
                    row.setSubLabel(rs.getNString("category_name"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public List<ReportRow> paymentSummary(Connection con, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        String sql = "SELECT p.method, p.payment_status, COUNT(*) AS cnt, SUM(p.amount) AS total " +
                     "FROM dbo.Payment p WHERE p.created_at BETWEEN ? AND ? " +
                     "GROUP BY p.method, p.payment_status ORDER BY p.method, p.payment_status";
        List<ReportRow> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, from);
            JdbcSupport.setDateTime(ps, 2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReportRow row = new ReportRow(label(rs.getString("method")),
                            rs.getLong("cnt"), rs.getBigDecimal("total"));
                    row.setSubLabel(rs.getString("payment_status"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public List<ReportRow> revenueByDay(Connection con, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        String sql = "SELECT CAST(p.paid_at AS DATE) AS d, SUM(p.amount) AS net " +
                     "  FROM dbo.Payment p WHERE p.paid_at BETWEEN ? AND ? " +
                     " GROUP BY CAST(p.paid_at AS DATE) ORDER BY 1";
        List<ReportRow> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, from);
            JdbcSupport.setDateTime(ps, 2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ReportRow(rs.getDate("d").toString(), 0, rs.getBigDecimal("net")));
                }
            }
        }
        return list;
    }

    private String label(String method) {
        return "CASH".equals(method) ? "Tiền mặt" : "Thanh toán online";
    }
}
