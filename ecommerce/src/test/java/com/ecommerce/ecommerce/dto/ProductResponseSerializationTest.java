package com.ecommerce.ecommerce.dto;

import com.ecommerce.ecommerce.entity.Product;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductResponseSerializationTest {

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

    @Test
    void responseDtoSerializesWithTheSameFieldsAndValuesAsProductEntity() {
        Product product = Product.builder()
                .id(5L)
                .name("Woven Shirt")
                .description(null)
                .price(new BigDecimal("1250.00"))
                .category("Shirts")
                .color("Indigo")
                .imageUrl(null)
                .stock(7)
                .active(true)
                .createdAt(LocalDateTime.parse("2026-10-09T10:15:30"))
                .build();

        JsonNode entityJson = objectMapper.valueToTree(product);
        JsonNode responseJson = objectMapper.valueToTree(ProductResponse.from(product));

        assertEquals(entityJson, responseJson);
        assertEquals(10, responseJson.size());
        assertEquals("2026-10-09T10:15:30", responseJson.get("createdAt").asText());
    }
}
