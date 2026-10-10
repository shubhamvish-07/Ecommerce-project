package com.ecommerce.ecommerce.integration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ecommerce.ecommerce.dto.CreateOrderRequest;
import com.ecommerce.ecommerce.dto.OrderLineRequest;
import com.ecommerce.ecommerce.dto.OrderResponse;
import com.ecommerce.ecommerce.entity.Product;
import com.ecommerce.ecommerce.entity.User;
import com.ecommerce.ecommerce.enums.Role;
import com.ecommerce.ecommerce.exception.InsufficientStockException;
import com.ecommerce.ecommerce.repository.OrderRepository;
import com.ecommerce.ecommerce.repository.ProductRepository;
import com.ecommerce.ecommerce.repository.UserRepository;
import com.ecommerce.ecommerce.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class OrderApiIntegrationTest {

    private static final String PASSWORD = "test-password-123";

    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(
            new SimpleModule().addSerializer(LocalDateTime.class, new StdSerializer<>(LocalDateTime.class) {
                @Override
                public void serialize(
                        LocalDateTime value,
                        JsonGenerator generator,
                        SerializerProvider provider) throws IOException {
                    generator.writeString(value.toString());
                }
            })
    );

    private MockMvc mockMvc;
    private String suffix;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void coversAuthenticatedOrderAndAdminProductFlows() throws Exception {
        String userAEmail = "a-" + suffix + "@example.test";
        String userBEmail = "b-" + suffix + "@example.test";
        String adminEmail = "admin-" + suffix + "@example.test";
        String userAToken = registerAndLogin("Customer A", userAEmail);
        String userBToken = registerAndLogin("Customer B", userBEmail);

        userRepository.save(User.builder()
                .name("Test Admin")
                .email(adminEmail)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.ADMIN)
                .build());
        String adminToken = login(adminEmail);

        long productId = createProduct(adminToken, "<strong>Woven Shirt</strong>", 10, "12.50");
        JsonNode createResponse = readJson(mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals("<strong>Woven Shirt</strong>", createResponse.get(0).get("name").asText());
        assertTrue(createResponse.get(0).get("createdAt").isTextual());
        LocalDateTime.parse(createResponse.get(0).get("createdAt").asText());

        mockMvc.perform(get("/admin/orders").header("Authorization", bearer(userAToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/orders/1").header("Authorization", bearer(userAToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/admin/orders/1/status")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/products").header("Authorization", bearer(userAToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/user/orders")).andExpect(status().isUnauthorized());

        JsonNode placedOrder = readJson(mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(line(productId, 2), line(productId, 1))))
                .andExpect(status().isCreated())
                .andReturn());
        long orderId = placedOrder.get("id").asLong();
        assertEquals(0, new BigDecimal("37.50")
                .compareTo(new BigDecimal(placedOrder.get("totalAmount").asText())));
        assertEquals("PLACED", placedOrder.get("status").asText());
        assertEquals("COD", placedOrder.get("paymentMethod").asText());
        assertEquals("12 Weaver Lane", placedOrder.get("deliveryAddress").asText());
        assertEquals(1, placedOrder.get("items").size());
        assertEquals(3, placedOrder.get("items").get(0).get("quantity").asInt());
        assertEquals("M", placedOrder.get("items").get(0).get("size").asText());
        assertEquals("<strong>Woven Shirt</strong>",
                placedOrder.get("items").get(0).get("productName").asText());
        assertEquals(7, productRepository.findById(productId).orElseThrow().getStock());

        mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":0}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(line(productId, 1, "XXXL"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of("productId", productId, "quantity", 1))))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(line(999999L, 1))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(line(productId, 8))))
                .andExpect(status().isConflict());
        assertEquals(7, productRepository.findById(productId).orElseThrow().getStock());

        JsonNode ownOrder = readJson(mockMvc.perform(get("/user/orders/" + orderId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(orderId, ownOrder.get("id").asLong());
        assertTrue(ownOrder.get("customerEmail") == null);
        mockMvc.perform(get("/user/orders/" + orderId).header("Authorization", bearer(userBToken)))
                .andExpect(status().isNotFound());
        JsonNode ownOrders = readJson(mockMvc.perform(get("/user/orders")
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(1, ownOrders.size());

        JsonNode adminOrder = readJson(mockMvc.perform(get("/admin/orders")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()).get(0);
        assertEquals(userAEmail, adminOrder.get("customerEmail").asText());
        assertEquals("Customer A", adminOrder.get("customerName").asText());
        assertEquals("COD", adminOrder.get("paymentMethod").asText());
        assertEquals("12 Weaver Lane", adminOrder.get("deliveryAddress").asText());
        mockMvc.perform(get("/admin/orders?status=PLACED").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/orders/" + orderId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        updateStatus(adminToken, orderId, "SHIPPED", 200);
        updateStatus(adminToken, orderId, "DELIVERED", 200);
        updateStatus(adminToken, orderId, "SHIPPED", 409);
        assertEquals("DELIVERED", readJson(mockMvc.perform(get("/user/orders/" + orderId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andReturn()).get("status").asText());

        long secondOrderId = readJson(mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(line(productId, 2))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
        updateStatus(adminToken, secondOrderId, "CANCELLED", 200);
        assertEquals(7, productRepository.findById(productId).orElseThrow().getStock());
        updateStatus(adminToken, secondOrderId, "CANCELLED", 409);
        assertEquals(7, productRepository.findById(productId).orElseThrow().getStock());

        long rollbackProductId = createProduct(adminToken, "Rollback item", 1, "3.00");
        long ordersBeforeRollback = orderRepository.count();
        mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(line(productId, 1), line(rollbackProductId, 2))))
                .andExpect(status().isConflict());
        assertEquals(7, productRepository.findById(productId).orElseThrow().getStock());
        assertEquals(1, productRepository.findById(rollbackProductId).orElseThrow().getStock());
        assertEquals(ordersBeforeRollback, orderRepository.count());

        Product productBeforeEdit = productRepository.findById(productId).orElseThrow();
        mockMvc.perform(put("/admin/products/" + productId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Updated Shirt", 15, "14.00")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/admin/products/" + productId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"category\":\"Shirts\",\"price\":-1,\"stock\":0}"))
                .andExpect(status().isBadRequest());
        assertEquals("<strong>Woven Shirt</strong>",
                readJson(mockMvc.perform(get("/user/orders/" + orderId)
                                .header("Authorization", bearer(userAToken)))
                        .andExpect(status().isOk())
                        .andReturn()).get("items").get(0).get("productName").asText());
        assertEquals("<strong>Woven Shirt</strong>", productBeforeEdit.getName());

        long archivedProductId = createProduct(adminToken, "Archive Shirt", 2, "30.00");
        long archivedOrderId = readJson(mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(line(archivedProductId, 1))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
        mockMvc.perform(delete("/admin/products/" + archivedProductId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        assertEquals(0, productRepository.findByActiveTrue().stream()
                .filter(product -> product.getId().equals(archivedProductId)).count());
        assertEquals(0, readJson(mockMvc.perform(get("/products"))
                        .andExpect(status().isOk())
                        .andReturn()).findValuesAsText("id").stream()
                .filter(String.valueOf(archivedProductId)::equals).count());
        assertEquals(0, readJson(mockMvc.perform(get("/admin/products")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()).findValuesAsText("id").stream()
                .filter(String.valueOf(archivedProductId)::equals).count());
        assertEquals("Archive Shirt", readJson(mockMvc.perform(get("/user/orders/" + archivedOrderId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andReturn()).get("items").get(0).get("productName").asText());
        mockMvc.perform(post("/user/orders")
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(line(archivedProductId, 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deliveryAddressesAreIsolatedByAuthenticatedUser() throws Exception {
        String emailA = "profile-a-" + suffix + "@example.test";
        String emailB = "profile-b-" + suffix + "@example.test";
        String tokenA = registerAndLogin("Customer A", emailA);
        String tokenB = registerAndLogin("Customer B", emailB);

        mockMvc.perform(put("/user/profile")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("12 Weaver Lane")))
                .andExpect(status().isOk());

        JsonNode profileB = readJson(mockMvc.perform(get("/user/profile")
                        .header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(emailB, profileB.get("email").asText());
        assertTrue(profileB.get("deliveryAddress").isNull());

        mockMvc.perform(put("/user/profile")
                        .header("Authorization", bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("98 River Road")))
                .andExpect(status().isOk());

        JsonNode profileA = readJson(mockMvc.perform(get("/user/profile")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(emailA, profileA.get("email").asText());
        assertEquals("12 Weaver Lane", profileA.get("deliveryAddress").asText());
        assertEquals("98 River Road", readJson(mockMvc.perform(get("/user/profile")
                        .header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andReturn()).get("deliveryAddress").asText());
    }

    @Test
    void accountFailureLimitAppliesAcrossClientAddresses() throws Exception {
        String email = "locked-" + suffix + "@example.test";
        registerAndLogin("Test Customer", email);

        for (int attempt = 0; attempt < 10; attempt++) {
            int clientOctet = attempt + 1;
            String attemptEmail = attempt % 2 == 0
                    ? email.toUpperCase(java.util.Locale.ROOT)
                    : email;
            mockMvc.perform(post("/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100." + clientOctet);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", attemptEmail,
                                    "password", "incorrect-password"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.200");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertEquals("Invalid email or password",
                        readJson(result).get("message").asText()));

        String unknownEmail = "unknown-" + suffix + "@example.test";
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", unknownEmail,
                                    "password", "incorrect-password"))))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", unknownEmail,
                                "password", "incorrect-password"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertEquals("Invalid email or password",
                        readJson(result).get("message").asText()));
    }

    @Test
    void concurrentOrdersForOneRemainingUnitHaveExactlyOneSuccess() throws Exception {
        String email = "concurrent-" + suffix + "@example.test";
        userRepository.saveAndFlush(User.builder()
                .name("Concurrent Customer")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .build());
        Product product = productRepository.saveAndFlush(Product.builder()
                .name("One Unit")
                .price(new BigDecimal("10.00"))
                .category("Test")
                .stock(1)
                .active(true)
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReferenceArray<Object> results = new AtomicReferenceArray<>(2);
        Thread first = placeLastUnit(email, product.getId(), ready, start, results, 0);
        Thread second = placeLastUnit(email, product.getId(), ready, start, results, 1);
        first.start();
        second.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        first.join(10000);
        second.join(10000);

        int successes = 0;
        int stockConflicts = 0;
        for (int index = 0; index < results.length(); index++) {
            Object result = results.get(index);
            if (result instanceof OrderResponse) successes++;
            if (result instanceof InsufficientStockException) stockConflicts++;
        }
        assertEquals(1, successes);
        assertEquals(1, stockConflicts);
        assertEquals(0, productRepository.findById(product.getId()).orElseThrow().getStock());
        assertEquals(1, orderRepository.count());
    }

    private String registerAndLogin(String name, String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", email,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk());
        return login(email);
    }

    private String login(String email) throws Exception {
        JsonNode response = readJson(mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn());
        return response.get("accessToken").asText();
    }

    private long createProduct(String token, String name, int stock, String price) throws Exception {
        JsonNode response = readJson(mockMvc.perform(post("/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, stock, price)))
                .andExpect(status().isCreated())
                .andReturn());
        return response.get("id").asLong();
    }

    private String productJson(String name, int stock, String price) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "description", "Test product description",
                "price", new BigDecimal(price),
                "category", "Shirts",
                "color", "Blue",
                "stock", stock
        ));
    }

    private String orderJson(OrderLineRequest... lines) throws Exception {
        List<Map<String, Object>> items = List.of(lines).stream()
                .map(line -> Map.<String, Object>of(
                        "productId", line.getProductId(),
                        "quantity", line.getQuantity(),
                        "size", line.getSize()
                ))
                .toList();
        return objectMapper.writeValueAsString(Map.of(
                "items", items,
                "deliveryAddress", Map.of(
                        "name", "Test Customer",
                        "phone", "9876543210",
                        "addressLine", "12 Weaver Lane",
                        "city", "Mumbai",
                        "state", "Maharashtra",
                        "postalCode", "400001"
                )));
    }

    private String addressJson(String addressLine) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", "Test Customer",
                "phone", "9876543210",
                "addressLine", addressLine,
                "city", "Mumbai",
                "state", "Maharashtra",
                "postalCode", "400001"
        ));
    }

    private OrderLineRequest line(Long productId, int quantity) {
        return line(productId, quantity, "M");
    }

    private OrderLineRequest line(Long productId, int quantity, String size) {
        OrderLineRequest line = new OrderLineRequest();
        line.setProductId(productId);
        line.setQuantity(quantity);
        line.setSize(size);
        return line;
    }

    private void updateStatus(String token, long orderId, String status, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/admin/orders/" + orderId + "/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Thread placeLastUnit(
            String email,
            Long productId,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicReferenceArray<Object> results,
            int index) {
        return new Thread(() -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    results.set(index, new IllegalStateException("Start timed out"));
                    return;
                }
                CreateOrderRequest request = new CreateOrderRequest();
                request.setItems(List.of(line(productId, 1)));
                var address = new com.ecommerce.ecommerce.dto.DeliveryAddressRequest();
                address.setName("Concurrent Customer");
                address.setPhone("9876543210");
                address.setAddressLine("12 Weaver Lane");
                address.setCity("Mumbai");
                address.setState("Maharashtra");
                address.setPostalCode("400001");
                request.setDeliveryAddress(address);
                results.set(index, orderService.placeOrder(email, request));
            } catch (Throwable exception) {
                results.set(index, exception);
            }
        });
    }
}
