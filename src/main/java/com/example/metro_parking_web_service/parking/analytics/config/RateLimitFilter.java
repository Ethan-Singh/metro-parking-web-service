/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final ParkingSecurityProperties securityProperties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String path = request.getRequestURI();

        Bucket bucket = buckets.computeIfAbsent(clientIp, this::newBucket);

        if (bucket.tryConsume(1)) {
            log.debug("event=rate_limit_check decision=allow ip={} path={}", clientIp, path);
            filterChain.doFilter(request, response);
        } else {
            log.warn(
                    "event=rate_limit_exceeded decision=block ip={} path={} limit={}"
                            + " refillTokens={} refillPeriodSeconds={}",
                    clientIp,
                    path,
                    securityProperties.rateLimit().capacity(),
                    securityProperties.rateLimit().refillTokens(),
                    securityProperties.rateLimit().refillPeriodSeconds());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");

            response.getWriter()
                    .write(
                            """
                            {"error":"Too many requests"}
                            """);
        }
    }

    private Bucket newBucket(String clientIp) {
        ParkingSecurityProperties.RateLimit ratelimit = securityProperties.rateLimit();
        log.debug(
                "event=rate_limit_bucket_create ip={} capacity={} refillTokens={}"
                        + " refillPeriodSeconds={}",
                clientIp,
                ratelimit.capacity(),
                ratelimit.refillTokens(),
                ratelimit.refillPeriodSeconds());

        Bandwidth limit =
                Bandwidth.builder()
                        .capacity(ratelimit.capacity())
                        .refillGreedy(
                                ratelimit.refillTokens(),
                                Duration.ofSeconds(ratelimit.refillPeriodSeconds()))
                        .build();

        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
