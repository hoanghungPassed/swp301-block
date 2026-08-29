package com.fastfood.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DBContext {

    private static final Logger LOG = Logger.getLogger(DBContext.class.getName());
    private static HikariDataSource dataSource;

    private DBContext() {
    }

    /** Đọc db.properties và khởi tạo HikariCP connection pool đúng một lần. */
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
        cfg.setAutoCommit(true);

        dataSource = new HikariDataSource(cfg);
        LOG.info("DBContext: da khoi tao connection pool toi " + p.getProperty("db.url"));
    }

    /** Đóng connection pool khi ứng dụng/Tomcat dừng. */
    public static synchronized void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            LOG.info("DBContext: da dong connection pool.");
        }
    }

    /** Mượn một JDBC Connection từ pool, tự khởi tạo pool nếu cần. */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            init();
        }
        return dataSource.getConnection();
    }

    /** Kiểm tra nhanh ứng dụng có kết nối hợp lệ tới SQL Server hay không. */
    public static boolean testConnection() {
        try (Connection con = getConnection()) {
            return con.isValid(3);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "DBContext: khong ket noi duoc toi SQL Server", e);
            return false;
        }
    }

    /** Đọc cấu hình database UTF-8 từ classpath db.properties. */
    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = DBContext.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IllegalStateException("Khong tim thay db.properties trong classpath");
            }
            /* UTF-8 tường minh, cùng lý do như AppConfig: mật khẩu cơ sở dữ liệu có
               dấu hoặc ký tự ngoài ASCII sẽ sai âm thầm nếu để mặc định ISO-8859-1. */
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Khong doc duoc db.properties", e);
        }
        return p;
    }
}
