package com.fastfood.scheduler;

import com.fastfood.service.shared.ScheduleService;

import java.util.logging.Level;
import java.util.logging.Logger;

public class KitchenReleaseScheduler implements Runnable {

    private static final Logger LOG = Logger.getLogger(KitchenReleaseScheduler.class.getName());

    private final ScheduleService scheduleService = new ScheduleService();

    @Override
    /** Được timer gọi định kỳ để đưa các đơn đến giờ chuẩn bị vào hàng chờ bếp. */
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
