package com.fastfood.service;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.exception.DataAccessException;
import com.fastfood.config.DBContext;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Quản lý giao dịch cơ sở dữ liệu cho tầng Service.
 * <p>
 * Đặt ở đây thay vì rải try/catch trong từng phương thức, để mọi thao tác nhiều bước đều
 * chắc chắn có commit hoặc rollback. Đây là điều kiện bắt buộc với các nghiệp vụ như
 * ghi nhận thanh toán rồi xác nhận đơn: nếu bước sau hỏng mà bước trước đã lưu thì
 * khách mất tiền nhưng đơn không được nhận.
 * <p>
 * Lỗi nghiệp vụ ({@link AppException}) được ném tiếp nguyên vẹn để tầng trên hiển thị
 * thông báo cho người dùng; lỗi SQL được bọc lại để tầng trên không phải phụ thuộc java.sql.
 */
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

    /** Chỉ đọc: không cần giao dịch, trả kết nối về pool ngay sau khi xong. */
    public static <T> T read(Work<T> work) {
        try (Connection con = DBContext.getConnection()) {
            return work.run(con);
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi truy vấn cơ sở dữ liệu.", e);
        }
    }

    /** Ghi nhiều bước: tự commit khi thành công, tự rollback khi có bất kỳ lỗi nào. */
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
            // Bao gồm cả lỗi nghiệp vụ do Service chủ động ném ra giữa chừng
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
                // Kết nối đã hỏng; pool sẽ loại bỏ nó khi đóng
            }
        }
    }

    private static void close(Connection con) {
        if (con != null) {
            try {
                con.setAutoCommit(true);
                con.close();
            } catch (SQLException ignored) {
                // Không còn gì làm được ở đây
            }
        }
    }
}
