package com.fastfood.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Điểm truy cập duy nhất tới SQL Server.
 * <p>
 * Dùng connection pool vì màn hình bếp truy vấn lại mỗi 2 giây và Scheduler chạy mỗi 30 giây —
 * mở/đóng kết nối mới cho từng lần sẽ tốn hơn nhiều so với chi phí giữ pool.
 * <p>
 * Tầng Service chịu trách nhiệm mở, commit và đóng kết nối; tầng DAO chỉ nhận
 * {@link Connection} có sẵn để nhiều thao tác nằm chung một giao dịch.
 */
public final class DBContext {

    private static final Logger LOG = Logger.getLogger(DBContext.class.getName());
    private static HikariDataSource dataSource;

    private DBContext() {
    }

    /** Gọi một lần lúc ứng dụng khởi động (AppContextListener). */
    public static synchronized void init() {
        if (dataSource != null) {
            return;
        }
        Properties p = load();
        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName(p.getProperty("db.driver"));
        cfg.setJdbcUrl(p.getProperty("db.url"));
        cfg.setUsername(p.getProperty("db.username"));
        cfg.setPassword(p.getProperty("db.password"));
        cfg.setMaximumPoolSize(Integer.parseInt(p.getProperty("db.pool.maximumPoolSize", "20")));
        cfg.setMinimumIdle(Integer.parseInt(p.getProperty("db.pool.minimumIdle", "5")));
        cfg.setConnectionTimeout(Long.parseLong(p.getProperty("db.pool.connectionTimeout", "30000")));
        cfg.setIdleTimeout(Long.parseLong(p.getProperty("db.pool.idleTimeout", "600000")));
        cfg.setPoolName("FastFoodPool");
        // Mặc định bật auto-commit; Service tự tắt khi cần chạy giao dịch nhiều bước.
        cfg.setAutoCommit(true);

        dataSource = new HikariDataSource(cfg);
        LOG.info("DBContext: da khoi tao connection pool toi " + p.getProperty("db.url"));
    }

    public static synchronized void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            LOG.info("DBContext: da dong connection pool.");
        }
    }

    /**
     * Lấy một kết nối từ pool. Luôn dùng trong try-with-resources để trả về pool.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            init();
        }
        return dataSource.getConnection();
    }

    /** Kiểm tra kết nối lúc khởi động để lỗi cấu hình lộ ra ngay, không đợi tới request đầu tiên. */
    public static boolean testConnection() {
        try (Connection con = getConnection()) {
            return con.isValid(3);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "DBContext: khong ket noi duoc toi SQL Server", e);
            return false;
        }
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = DBContext.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IllegalStateException("Khong tim thay db.properties trong classpath");
            }
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Khong doc duoc db.properties", e);
        }
        return p;
    }
}
