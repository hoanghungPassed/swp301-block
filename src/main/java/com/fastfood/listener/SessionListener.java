package com.fastfood.listener;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionListener implements HttpSessionListener {

    private static final AtomicInteger ACTIVE_SESSIONS = new AtomicInteger();

    public static int getActiveSessions() {
        return ACTIVE_SESSIONS.get();
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        ACTIVE_SESSIONS.incrementAndGet();
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        ACTIVE_SESSIONS.decrementAndGet();
    }
}
