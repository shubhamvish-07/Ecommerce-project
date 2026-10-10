package com.ecommerce.ecommerce.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class LoginAttemptTracker {

    private final AuthRateLimitProperties properties;
    private final Cache<String, Failures> failures;

    public LoginAttemptTracker(AuthRateLimitProperties properties) {
        this.properties = properties;
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(properties.getWindowMinutes()))
                .maximumSize(properties.getMaximumEntries())
                .build();
    }

    public long retryAfterSeconds(String normalizedEmail) {
        Failures state = failures.getIfPresent(normalizedEmail);
        if (state == null) {
            return 0;
        }
        long remainingNanos = state.blockedUntilNanos() - System.nanoTime();
        return remainingNanos <= 0 ? 0 : Math.max(1, (long) Math.ceil(
                (double) remainingNanos / TimeUnit.SECONDS.toNanos(1)));
    }

    public void recordFailure(String normalizedEmail) {
        long now = System.nanoTime();
        failures.asMap().compute(normalizedEmail, (email, current) -> {
            int count = current == null ? 0 : current.count();
            int level = current == null ? 0 : current.cooldownLevel();
            int nextCount = count + 1;
            if (nextCount < properties.getAccountFailures()) {
                return new Failures(nextCount, level, 0);
            }

            long cooldownSeconds = Math.min(30, 1L << Math.min(level, 5));
            return new Failures(nextCount, Math.min(level + 1, 5),
                    now + TimeUnit.SECONDS.toNanos(cooldownSeconds));
        });
    }

    public void clear(String normalizedEmail) {
        failures.invalidate(normalizedEmail);
    }

    private record Failures(int count, int cooldownLevel, long blockedUntilNanos) {
    }
}
