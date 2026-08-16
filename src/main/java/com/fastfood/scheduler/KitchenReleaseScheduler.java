package com.fastfood.scheduler;

import com.fastfood.service.shared.ScheduleService;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Công việc chạy nền đưa đơn đặt trước xuống bếp đúng giờ.
 * <p>
 * Đây là thứ làm cho mô hình đặt trước có ý nghĩa: đơn đã thanh toán vẫn nằm chờ,
 * chỉ tới sát giờ hẹn mới xuất hiện trên màn hình bếp. Món vì thế không bị làm sớm rồi
 * để nguội, mà khách tới vẫn có món ngay.
 * <p>
 * Bắt mọi lỗi và không để lọt ra ngoài: một lượt chạy hỏng mà ném lỗi ra thì bộ hẹn giờ
 * của Java sẽ dừng hẳn công việc này, và từ đó không đơn nào xuống bếp nữa.
 */
public class KitchenReleaseScheduler implements Runnable {

    private static final Logger LOG = Logger.getLogger(KitchenReleaseScheduler.class.getName());

    private final ScheduleService scheduleService = new ScheduleService();

    @Override
    public void run() {
        try {
            int released = scheduleService.releaseDueOrders();
            if (released > 0) {
                LOG.info("Bo hen gio: da dua " + released + " don xuong bep.");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Loi khi dua don xuong bep", e);
        }
    }
}
