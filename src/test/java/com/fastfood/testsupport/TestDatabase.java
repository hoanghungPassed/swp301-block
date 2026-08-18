package com.fastfood.testsupport;

import com.fastfood.config.AppConfig;
import com.fastfood.config.DBContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class TestDatabase {

    private static final String SCHEMA_FILE = "database/FastFoodPreorder.sql";

    private static boolean prepared;
    private static boolean available;
    private static boolean schemaBroken;
    private static String unavailableReason;

    private TestDatabase() {
    }

    public static synchronized boolean ensureReady() {
        if (prepared) {
            if (!available && schemaBroken) {
                throw new IllegalStateException(unavailableReason);
            }
            return available;
        }
        prepared = true;
        try {
            AppConfig.init();
            available = canConnect();
            if (!available) {
                return false;
            }
            rebuildWithRetry();
        } catch (Exception e) {
            available = false;
            schemaBroken = true;
            unavailableReason = "Tep luoc do khong chay lai duoc — day la loi ma nguon, "
                    + "khong phai moi truong thieu.\n" + e.getMessage();
            throw new IllegalStateException(unavailableReason, e);
        }
        return true;
    }

    private static boolean canConnect() {
        try {
            Properties p = loadTestProperties();
            String masterUrl = p.getProperty("db.url").replaceFirst(
                    "databaseName=[^;]+", "databaseName=master");
            try (java.sql.Connection ignored = java.sql.DriverManager.getConnection(
                    masterUrl, p.getProperty("db.username"), p.getProperty("db.password"))) {
                return true;
            }
        } catch (Exception e) {
            unavailableReason = e.getClass().getSimpleName() + ": " + e.getMessage();
            return false;
        }
    }

    public static String unavailableReason() {
        return unavailableReason == null ? "khong ro" : unavailableReason;
    }

    private static final int DEADLOCK_VICTIM = 1205;
    private static final int LOCK_TIMEOUT = 1222;
    private static final int LAN_THU_TOI_DA = 3;

    private static void rebuildWithRetry() throws Exception {
        for (int lan = 1; ; lan++) {
            try {
                rebuild();
                return;
            } catch (Exception e) {
                if (lan >= LAN_THU_TOI_DA || !laTranhChapKhoa(e)) {
                    throw e;
                }
                Thread.sleep(1000L * lan);
            }
        }
    }

    private static boolean laTranhChapKhoa(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && (sql.getErrorCode() == DEADLOCK_VICTIM || sql.getErrorCode() == LOCK_TIMEOUT)) {
                return true;
            }
        }
        return false;
    }

    private static void rebuild() throws Exception {
        Properties p = loadTestProperties();
        String targetDb = databaseNameOf(p.getProperty("db.url"));

        DBContext.shutdown();

        String script = Files.readString(projectFile(SCHEMA_FILE), StandardCharsets.UTF_8)
                .replace("FastFoodPreorder", targetDb);

        String masterUrl = p.getProperty("db.url").replace("databaseName=" + targetDb, "databaseName=master");
        Class.forName(p.getProperty("db.driver"));

        try (Connection con = DriverManager.getConnection(
                masterUrl, p.getProperty("db.username"), p.getProperty("db.password"))) {
            for (String batch : splitOnGo(script)) {
                try (Statement st = con.createStatement()) {
                    st.execute(batch);
                    while (st.getMoreResults() || st.getUpdateCount() != -1) {
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException(
                            "Loi khi chay schema test.\nLo lenh:\n" + preview(batch) + "\nLoi: " + e.getMessage(), e);
                }
            }
        }
    }

    private static List<String> splitOnGo(String script) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\r?\n")) {
            if (line.trim().equalsIgnoreCase("GO")) {
                addIfMeaningful(batches, current);
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        addIfMeaningful(batches, current);
        return batches;
    }

    private static void addIfMeaningful(List<String> batches, StringBuilder sb) {
        String batch = sb.toString().trim();
        if (!batch.isEmpty()) {
            batches.add(batch);
        }
    }

    private static String preview(String batch) {
        return batch.length() <= 400 ? batch : batch.substring(0, 400) + " ...";
    }

    private static Properties loadTestProperties() throws IOException {
        Properties p = new Properties();
        try (InputStream in = TestDatabase.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IllegalStateException("Khong tim thay db.properties trong classpath test");
            }
            p.load(in);
        }
        return p;
    }

    private static String databaseNameOf(String jdbcUrl) {
        for (String part : jdbcUrl.split(";")) {
            if (part.startsWith("databaseName=")) {
                return part.substring("databaseName=".length());
            }
        }
        throw new IllegalStateException("db.url thieu databaseName: " + jdbcUrl);
    }

    private static Path projectFile(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("pom.xml"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Khong tim thay thu muc goc du an (khong thay pom.xml)");
        }
        Path file = dir.resolve(relative);
        if (!Files.exists(file)) {
            throw new IllegalStateException("Khong tim thay " + file);
        }
        return file;
    }
}
