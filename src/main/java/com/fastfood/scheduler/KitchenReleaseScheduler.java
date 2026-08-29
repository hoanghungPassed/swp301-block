package com.fastfood.scheduler;

import com.fastfood.service.shared.ScheduleService;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tác vụ chạy ngầm định kỳ :
 * Quét CSDL để tự động giải phóng các đơn hàng đặt trước (Online Pre-order) xuống bộ phận Bếp (KDS)
 * khi đến mốc thời gian chế biến (kitchen_release_at <= NOW).
 */
public class KitchenReleaseScheduler implements Runnable {

    private static final Logger LOG = Logger.getLogger(KitchenReleaseScheduler.class.getName());

    // Dịch vụ điều phối nghiệp vụ giải phóng đơn
    private final ScheduleService scheduleService = new ScheduleService();

    @Override
    /** Được timer gọi định kỳ để đưa các đơn đến giờ chuẩn bị vào hàng chờ bếp. */
    public void run() {
        try {
            // Thực hiện giải phóng các đơn hàng đã đến hạn vào bếp
            int released = scheduleService.releaseDueOrders();
            if (released > 0) {
                LOG.info("Bộ hẹn giờ: đã đưa " + released + " đơn xuống bếp.");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Lỗi khi đưa đơn xuống bếp", e);
        }
    }
}
