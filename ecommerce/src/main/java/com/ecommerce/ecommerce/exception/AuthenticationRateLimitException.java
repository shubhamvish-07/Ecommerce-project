package com.ecommerce.ecommerce.exception;

public class AuthenticationRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public AuthenticationRateLimitException(long retryAfterSeconds) {
        super("Invalid email or password");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
