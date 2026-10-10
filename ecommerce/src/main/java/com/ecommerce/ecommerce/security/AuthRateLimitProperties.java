package com.ecommerce.ecommerce.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public class AuthRateLimitProperties {

    @Min(1)
    private int loginAttempts = 20;
    @Min(1)
    private int registrationAttempts = 10;
    @Min(1)
    private int accountFailures = 10;
    @Min(1)
    private long windowMinutes = 15;
    @Min(1)
    private long maximumEntries = 100_000;

    public int getLoginAttempts() {
        return loginAttempts;
    }

    public void setLoginAttempts(int loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    public int getRegistrationAttempts() {
        return registrationAttempts;
    }

    public void setRegistrationAttempts(int registrationAttempts) {
        this.registrationAttempts = registrationAttempts;
    }

    public int getAccountFailures() {
        return accountFailures;
    }

    public void setAccountFailures(int accountFailures) {
        this.accountFailures = accountFailures;
    }

    public long getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(long windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public long getMaximumEntries() {
        return maximumEntries;
    }

    public void setMaximumEntries(long maximumEntries) {
        this.maximumEntries = maximumEntries;
    }
}
