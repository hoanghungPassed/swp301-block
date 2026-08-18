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

    public static <T> T read(Work<T> work) {
        try (Connection con = DBContext.getConnection()) {
            return work.run(con);
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi truy vấn cơ sở dữ liệu.", e);
        }
    }

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

    public static void writeVoid(VoidWork work) {
        write(con -> {
            work.run(con);
            return null;
        });
    }

    private static void rollback(Connection con) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ignored) {
            }
        }
    }

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
