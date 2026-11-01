package com.ecommerce.ecommerce.service.impl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudinaryUploaderTest {

    private static final String SECRET = "test-secret";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void startFakeCloudinary() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.ISO_8859_1
            );
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(body);

            Map<String, String> signedFields = new HashMap<>();
            for (String name : List.of("timestamp", "folder", "signature")) {
                String marker = "name=\"" + name + "\"\r\n\r\n";
                int start = body.indexOf(marker);
                if (start >= 0) {
                    int valueStart = start + marker.length();
                    signedFields.put(name, body.substring(valueStart, body.indexOf("\r\n", valueStart)));
                }
            }
            String signature = signedFields.remove("signature");
            boolean valid = CloudinaryUploader.sign(signedFields, SECRET).equals(signature);

            String response = valid
                    ? "{\"secure_url\":\"https:\\/\\/res.cloudinary.com\\/demo\\/image\\/upload\\/v1\\/p.jpg\"}"
                    : "{\"error\":{\"message\":\"Invalid Signature\"}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 401, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFakeCloudinary() {
        server.stop(0);
    }

    private CloudinaryUploader uploader(String secret) {
        return new CloudinaryUploader(
                "democloud",
                "key123",
                secret,
                "mul/products",
                baseUrl,
                HttpClient.newHttpClient(),
                () -> 1_700_000_000L
        );
    }

    @Test
    void signsCloudinaryFields() {
        assertEquals(
                "a21ad0f63beb4de2e5575204b79ab90bffb02c10",
                CloudinaryUploader.sign(Map.of("timestamp", "1315060510"), "abcd")
        );
    }

    @Test
    void uploadsSignedMultipartAndReturnsHttpsUrl() throws Exception {
        String url = uploader(SECRET).upload(new byte[]{1, 2, 3}, "product.jpg", "image/jpeg");

        assertEquals("https://res.cloudinary.com/demo/image/upload/v1/p.jpg", url);
        assertEquals("/v1_1/democloud/image/upload", requestPath.get());
        assertTrue(requestBody.get().contains("name=\"api_key\"\r\n\r\nkey123"));
        assertTrue(requestBody.get().contains("name=\"folder\"\r\n\r\nmul/products"));
        assertTrue(requestBody.get().contains("filename=\"product.jpg\""));
    }

    @Test
    void reportsCloudinaryRejection() {
        IOException exception = assertThrows(
                IOException.class,
                () -> uploader("wrong-secret").upload(new byte[]{1}, "product.jpg", "image/jpeg")
        );

        assertTrue(exception.getMessage().contains("HTTP 401"));
        assertTrue(exception.getMessage().contains("Invalid Signature"));
    }

    @Test
    void addsDeliveryTransformationToCloudinaryUrl() {
        assertEquals(
                "https://res.cloudinary.com/demo/image/upload/f_auto,q_auto,w_1400,c_limit/v1/p.jpg",
                CloudinaryImageStorageService.withDeliveryTransformation(
                        "https://res.cloudinary.com/demo/image/upload/v1/p.jpg"
                )
        );
    }
}
