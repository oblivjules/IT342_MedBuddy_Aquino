package com.medbuddy.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter: 100 requests per minute per IP for paths under /api/**.
 * Not distributed; keeps implementation intentionally minimal.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L; // 1 minute
    private static final int LIMIT = 100;

    private static class Window {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);
    }

    private final Map<String, Window> ipWindows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // Only apply to API endpoints
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // CORS preflight requests (OPTIONS) must bypass rate limiting and always succeed
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            addCorsHeaders(request, response);
            response.setStatus(200);
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);
        long now = Instant.now().toEpochMilli();

        Window w = ipWindows.computeIfAbsent(ip, k -> new Window());

        synchronized (w) {
            if (now - w.windowStart > WINDOW_MS) {
                w.windowStart = now;
                w.count.set(0);
            }

            int current = w.count.incrementAndGet();
            if (current > LIMIT) {
                addCorsHeaders(request, response);
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Too many requests\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return;
        }

        response.setHeader("Access-Control-Allow-Origin", origin);
        response.addHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type,Accept,Origin,X-Requested-With");
        response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // may be comma-separated list
            return xff.split(",")[0].trim();
        }
        String ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }
}
