package com.ecommerce.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private final AuthRateLimitProperties properties;
    private final Cache<String, Integer> attempts;
    private final AtomicBoolean clientPrefixLogged = new AtomicBoolean();

    public AuthRateLimitFilter(AuthRateLimitProperties properties) {
        this.properties = properties;
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(properties.getWindowMinutes()))
                .maximumSize(properties.getMaximumEntries())
                .build();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path;
        try {
            path = UriUtils.decode(
                    request.getRequestURI().substring(request.getContextPath().length()),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!"POST".equals(request.getMethod())
                || !("/auth/login".equals(path) || "/auth/register".equals(path))) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean login = "/auth/login".equals(path);
        String networkPrefix = clientPrefix(request.getRemoteAddr());
        if (clientPrefixLogged.compareAndSet(false, true)) {
            logger.info("Client network prefix resolved for the first auth request: {}", networkPrefix);
        }
        String clientKey = (login ? "login|" : "register|") + networkPrefix;
        int limit = login
                ? properties.getLoginAttempts()
                : properties.getRegistrationAttempts();
        if (!tryRecord(clientKey, limit)) {
            reject(response);
            return;
        }

        boolean refund = login;
        try {
            filterChain.doFilter(request, response);
            int status = response.getStatus();
            refund = login && status != HttpServletResponse.SC_UNAUTHORIZED
                    && status != HttpServletResponse.SC_FORBIDDEN;
        } finally {
            if (refund) {
                release(clientKey);
            }
        }
    }

    private boolean tryRecord(String key, int limit) {
        AtomicBoolean accepted = new AtomicBoolean();
        attempts.asMap().compute(key, (ignored, current) -> {
            int currentCount = current == null ? 0 : current;
            if (currentCount >= limit) {
                return currentCount;
            }
            accepted.set(true);
            return currentCount + 1;
        });
        return accepted.get();
    }

    private void release(String key) {
        attempts.asMap().computeIfPresent(key, (ignored, count) -> count > 1 ? count - 1 : null);
    }

    private String clientPrefix(String remoteAddress) {
        try {
            byte[] address = InetAddress.getByName(remoteAddress).getAddress();
            if (address.length == 4) {
                return "v4:" + InetAddress.getByAddress(address).getHostAddress();
            }
            StringBuilder prefix = new StringBuilder("v6:");
            for (int i = 0; i < 8; i++) {
                if (i > 0 && i % 2 == 0) {
                    prefix.append(':');
                }
                prefix.append(String.format(Locale.ROOT, "%02x", address[i] & 0xff));
            }
            return prefix.toString();
        } catch (UnknownHostException exception) {
            return "unknown:" + remoteAddress;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(properties.getWindowMinutes() * 60));
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(
                "{\"status\":429,\"message\":\"Too many authentication attempts\","
                        + "\"errors\":null}");
    }
}
