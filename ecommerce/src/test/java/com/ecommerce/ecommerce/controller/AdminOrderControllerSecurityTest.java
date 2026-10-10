package com.ecommerce.ecommerce.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
        "app.rate-limit.login-attempts=5",
        "app.rate-limit.registration-attempts=10",
        "app.rate-limit.account-failures=100"
})
@ActiveProfiles("test")
@WithMockUser(roles = "USER")
class AdminOrderControllerSecurityTest {

    @Autowired
    private WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void userCannotAccessAnyAdminOrderEndpoint() throws Exception {
        mockMvc.perform(get("/admin/products"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/test"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/orders/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/admin/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void anonymousUsersCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/products"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorsCanAccessAdminReadEndpoints() throws Exception {
        mockMvc.perform(get("/admin/products"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isOk());
    }

    @Test
    void loginAttemptsAreRateLimitedPerClientAddress() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.42");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.42");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"unknown@example.com\",\"password\":\"not-a-valid-password\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRequestURI("/auth/%6cogin");
                            request.setRemoteAddr("198.51.100.42");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\",\"password\":\"not-a-valid-password\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.42");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\",\"password\":\"not-a-valid-password\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void ipv6AddressesInTheSameSubnetShareLoginLimit() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            String address = attempt < 3
                    ? "2001:db8:abcd:1::1"
                    : "2001:db8:abcd:1::2";
            mockMvc.perform(post("/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(address);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"unknown@example.com\",\"password\":\"not-a-valid-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("2001:db8:abcd:1::3");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\",\"password\":\"not-a-valid-password\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void registrationAttemptsAreLimitedPerClientAddress() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/auth/register")
                            .with(request -> {
                                request.setRemoteAddr("192.0.2.123");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/auth/register")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.123");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void malformedAuthPathVariantsAreRejected() throws Exception {
        for (String path : new String[]{"/auth//login", "/auth/login;x=1"}) {
            mockMvc.perform(post("/auth/login")
                            .with(request -> {
                                request.setRequestURI(path);
                                request.setRemoteAddr("192.0.2.200");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"unknown@example.com\",\"password\":\"incorrect-password\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
