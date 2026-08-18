package com.fastfood.testsupport;

import com.fastfood.config.DBContext;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class IntegrationTestBase {

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(TestDatabase.ensureReady(),
                () -> "Bo qua: khong ket noi duoc SQL Server test — " + TestDatabase.unavailableReason());
    }

    protected static int exec(String sql, Object... params) {
        try (Connection con = DBContext.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Loi chay SQL: " + sql, e);
        }
    }

    protected static <T> T scalar(Class<T> type, String sql, Object... params) {
        try (Connection con = DBContext.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, type) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Loi chay SQL: " + sql, e);
        }
    }

    protected static int count(String sql, Object... params) {
        Integer n = scalar(Integer.class, sql, params);
        return n == null ? 0 : n;
    }

    protected static String text(String sql, Object... params) {
        return scalar(String.class, sql, params);
    }

    protected static BigDecimal money(String sql, Object... params) {
        return scalar(BigDecimal.class, sql, params);
    }

    protected static String expectSqlFailure(String sql, Object... params) {
        try (Connection con = DBContext.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            return e.getMessage();
        }
        fail("Cau lenh dang le phai bi chan nhung lai chay duoc: " + sql);
        return null;
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    protected static int userId(String email) {
        Integer id = scalar(Integer.class, "SELECT user_id FROM dbo.Users WHERE email = ?", email);
        if (id == null) {
            throw new IllegalStateException("Khong thay tai khoan mau: " + email);
        }
        return id;
    }

    protected static int anyOrderableProductId() {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 p.product_id FROM dbo.Product p " +
                "JOIN dbo.Category c ON c.category_id = p.category_id " +
                "WHERE p.status = 'ACTIVE' AND p.is_available = 1 AND c.status = 'ACTIVE' " +
                "ORDER BY p.product_id");
        if (id == null) {
            throw new IllegalStateException("Du lieu mau khong co mon nao dat duoc");
        }
        return id;
    }

    protected static int unavailableProductId() {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 product_id FROM dbo.Product " +
                "WHERE status <> 'ACTIVE' OR is_available = 0 ORDER BY product_id");
        if (id == null) {
            throw new IllegalStateException("Du lieu mau khong con mon nao ngung ban — "
                    + "cac bai test ve canh bao het hang mat cho de kiem chung");
        }
        return id;
    }

    protected static final String CUSTOMER_1 = "customer1@gmail.com";
    protected static final String CUSTOMER_2 = "customer2@gmail.com";
    protected static final String CASHIER_1 = "cashier1@fastfood.vn";
    protected static final String CASHIER_2 = "cashier2@fastfood.vn";
    protected static final String KITCHEN_1 = "kitchen1@fastfood.vn";
    protected static final String KITCHEN_2 = "kitchen2@fastfood.vn";
    protected static final String ADMIN = "admin@fastfood.vn";
}
