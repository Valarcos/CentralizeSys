package com.centralizesys.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MaintenanceInterceptor implements HandlerInterceptor {
    public static final AtomicBoolean isMaintenanceMode = new AtomicBoolean(false);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isMaintenanceMode.get()) {
            response.setStatus(503);
            response.getWriter().write("Service Unavailable - Maintenance Mode");
            return false;
        }
        return true;
    }
}
