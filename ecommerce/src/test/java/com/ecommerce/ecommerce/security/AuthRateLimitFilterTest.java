package com.ecommerce.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimitFilterTest {

    @Test
    void concurrentFailedLoginsCannotExceedTheConfiguredLimit() throws Exception {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setLoginAttempts(20);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties);
        int requestCount = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch enteredController = new CountDownLatch(properties.getLoginAttempts());
        CountDownLatch finishController = new CountDownLatch(1);
        AtomicInteger controllerCalls = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<?>> requests = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                requests.add(executor.submit(() -> {
                    start.await();
                    MockHttpServletRequest request =
                            new MockHttpServletRequest("POST", "/auth/login");
                    request.setRemoteAddr("198.51.100.10");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    FilterChain chain = (ignoredRequest, ignoredResponse) -> {
                        controllerCalls.incrementAndGet();
                        enteredController.countDown();
                        try {
                            if (!finishController.await(5, TimeUnit.SECONDS)) {
                                throw new ServletException("Timed out waiting to finish request");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new ServletException(exception);
                        }
                        ((HttpServletResponse) ignoredResponse).setStatus(401);
                    };
                    filter.doFilter(request, response, chain);
                    return null;
                }));
            }

            start.countDown();
            assertTrue(enteredController.await(5, TimeUnit.SECONDS));
            finishController.countDown();
            for (Future<?> request : requests) {
                request.get(5, TimeUnit.SECONDS);
            }
        } finally {
            finishController.countDown();
            executor.shutdownNow();
        }

        assertEquals(properties.getLoginAttempts(), controllerCalls.get());
    }

    @Test
    void invalidRequestDoesNotConsumeLoginAttempt() throws Exception {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setLoginAttempts(1);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties);

        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        filter.doFilter(loginRequest(), invalidResponse, (request, response) ->
                ((HttpServletResponse) response).setStatus(400));

        MockHttpServletResponse failedLoginResponse = new MockHttpServletResponse();
        AtomicInteger controllerCalls = new AtomicInteger();
        filter.doFilter(loginRequest(), failedLoginResponse, (request, response) -> {
            controllerCalls.incrementAndGet();
            ((HttpServletResponse) response).setStatus(401);
        });

        assertEquals(1, controllerCalls.get());
        assertEquals(401, failedLoginResponse.getStatus());
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("198.51.100.10");
        return request;
    }
}
