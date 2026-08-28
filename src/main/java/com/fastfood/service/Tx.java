package com.fastfood.service;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.exception.AppException.DataAccessException;
import com.fastfood.config.DBContext;

import java.sql.Connection;
import java.sql.SQLException;

public final class Tx {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection con) throws SQLException;
    }

    @FunctionalInterface
    public interface VoidWork {
        void run(Connection con) throws SQLException;
    }

    private Tx() {
    }

    /** Mở connection chỉ đọc, chạy DAO và tự đóng connection sau khi hoàn tất. */
    public static <T> T read(Work<T> work) {
        try (Connection con = DBContext.getConnection()) {
            return work.run(con);
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi truy vấn cơ sở dữ liệu.", e);
        }
    }

    /** Chạy nhiều thao tác ghi trong một transaction, commit khi thành công và rollback khi lỗi. */
    public static <T> T write(Work<T> work) {
        Connection con = null;
        try {
            con = DBContext.getConnection();
            con.setAutoCommit(false);
            T result = work.run(con);
            con.commit();
            return result;
        } catch (SQLException e) {
            rollback(con);
            throw new DataAccessException("Lỗi ghi dữ liệu.", e);
        } catch (RuntimeException e) {
            rollback(con);
            throw e;
        } finally {
            close(con);
        }
    }

    /** Biến thao tác ghi không có giá trị trả về thành một transaction dùng chung write(). */
    public static void writeVoid(VoidWork work) {
        write(con -> {
            work.run(con);
            return null;
        });
    }

    /** Cố gắng rollback connection khi transaction gặp lỗi. */
    private static void rollback(Connection con) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ignored) {
            }
        }
    }

    /** Khôi phục auto-commit và trả connection về pool. */
    private static void close(Connection con) {
        if (con != null) {
            try {
                con.setAutoCommit(true);
                con.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
