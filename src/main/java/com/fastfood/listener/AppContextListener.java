package com.fastfood.listener;

import com.fastfood.config.AppConfig;
import com.fastfood.config.DBContext;
import com.fastfood.scheduler.KitchenReleaseScheduler;
import com.fastfood.scheduler.PaymentExpiryScheduler;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Khởi động và dừng ứng dụng.
 * <p>
 * Kết nối cơ sở dữ liệu được thử ngay lúc khởi động để lỗi cấu hình lộ ra trong log máy chủ,
 * thay vì đợi tới khi người dùng đầu tiên bấm vào một trang nào đó mới báo lỗi.
 * <p>
 * Chỉ khai báo trong {@code WEB-INF/web.xml}, không kèm {@code @WebListener}: khai báo cả hai
 * nơi là để cùng một listener được đăng ký hai lần, và khi đó bộ hẹn giờ cũng chạy hai bản.
 */
public class AppContextListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(AppContextListener.class.getName());

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Tem phiên bản gắn vào địa chỉ tệp CSS và JS. Đổi sau mỗi lần khởi động nên sửa giao
        // diện xong chỉ cần chạy lại máy chủ là trình duyệt lấy bản mới, không phải Ctrl+F5
        // và cũng không có chuyện đang xem nhầm bản cũ mà tưởng mình sửa hỏng.
        sce.getServletContext().setAttribute("assetVersion",
                Long.toString(System.currentTimeMillis() / 1000L, 36));

        AppConfig.init();
        DBContext.init();

        if (DBContext.testConnection()) {
            LOG.info("Ket noi SQL Server thanh cong.");
        } else {
            LOG.severe("KHONG ket noi duoc SQL Server. Kiem tra src/main/resources/db.properties "
                     + "va bao dam da chay database/FastFoodPreorder.sql.");
        }

        // Hai luồng: một cho việc đưa đơn xuống bếp, một cho việc dọn đơn quá hạn,
        // để công việc này chậm không làm trễ công việc kia.
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "fastfood-scheduler");
            t.setDaemon(true);   // không giữ máy chủ lại khi tắt ứng dụng
            return t;
        });

        int releaseInterval = AppConfig.releaseIntervalSeconds();
        int expiryInterval = AppConfig.expiryIntervalSeconds();

        scheduler.scheduleWithFixedDelay(new KitchenReleaseScheduler(),
                5, releaseInterval, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(new PaymentExpiryScheduler(),
                15, expiryInterval, TimeUnit.SECONDS);

        LOG.info(String.format("Da bat bo hen gio: dua don xuong bep moi %d giay, "
                + "don don qua han moi %d giay.", releaseInterval, expiryInterval));
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        DBContext.shutdown();
        LOG.info("Da dung ung dung.");
    }
}
