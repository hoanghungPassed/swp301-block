package com.fastfood.scheduler;

import com.fastfood.service.shared.ScheduleService;

import java.util.logging.Level;
import java.util.logging.Logger;

public class PaymentExpiryScheduler implements Runnable {

    private static final Logger LOG = Logger.getLogger(PaymentExpiryScheduler.class.getName());

    private final ScheduleService scheduleService = new ScheduleService();

    @Override
    /** Được timer gọi định kỳ để hết hạn đơn online/POS không nhận được thanh toán. */
    public void run() {
        try {
            scheduleService.expireStalePayments();
            scheduleService.expireAbandonedCounterOrders();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Loi khi huy hieu luc don qua han", e);
        }
    }
}
