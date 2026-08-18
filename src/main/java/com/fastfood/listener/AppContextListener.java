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

public class AppContextListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(AppContextListener.class.getName());

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
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

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "fastfood-scheduler");
            t.setDaemon(true);
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
