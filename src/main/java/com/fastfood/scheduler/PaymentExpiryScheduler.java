package com.fastfood.scheduler;

import com.fastfood.service.shared.ScheduleService;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Công việc chạy nền huỷ hiệu lực các đơn quá hạn thanh toán.
 * <p>
 * Không có nó thì đơn khách bỏ dở sẽ nằm mãi ở trạng thái chờ thanh toán, làm màn hình
 * quản lý đầy rác và số liệu báo cáo sai lệch.
 */
public class PaymentExpiryScheduler implements Runnable {

    private static final Logger LOG = Logger.getLogger(PaymentExpiryScheduler.class.getName());

    private final ScheduleService scheduleService = new ScheduleService();

    @Override
    public void run() {
        try {
            scheduleService.expireStalePayments();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Loi khi huy hieu luc don qua han", e);
        }
    }
}
