package com.ecommerce.ecommerce.dto;

import com.ecommerce.ecommerce.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private String color;
    private String imageUrl;
    private Integer stock;
    private Boolean active;
    private LocalDateTime createdAt;

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getColor(),
                product.getImageUrl(),
                product.getStock(),
                product.getActive(),
                product.getCreatedAt()
        );
    }
}
