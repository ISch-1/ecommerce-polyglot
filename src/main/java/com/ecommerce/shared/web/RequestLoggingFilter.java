package com.ecommerce.shared.web;

import java.io.IOException;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Structured access log with traceId/spanId in the MDC so log lines and
 * distributed traces line up in Jaeger.
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            if (tracer.currentSpan() != null) {
                MDC.put("traceId", tracer.currentSpan().context().traceId());
                MDC.put("spanId", tracer.currentSpan().context().spanId());
            }
            chain.doFilter(req, res);
        } finally {
            long durMs = (System.nanoTime() - started) / 1_000_000L;
            log.info("{} {} → {} in {}ms",
                    req.getMethod(), req.getRequestURI(), res.getStatus(), durMs);
            MDC.clear();
        }
    }
}
