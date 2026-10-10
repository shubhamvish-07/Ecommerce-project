package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.dto.ProductRequest;
import com.ecommerce.ecommerce.entity.Product;
import com.ecommerce.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product existing;

    @BeforeEach
    void setUp() {
        existing = Product.builder()
                .id(3L)
                .name("Old name")
                .description("Old description")
                .price(new BigDecimal("10.00"))
                .category("Shirts")
                .color("Blue")
                .imageUrl("/uploads/products/old.jpg")
                .stock(2)
                .active(true)
                .build();
    }

    @Test
    void updatesFieldsAndPreservesExistingImageWhenNoReplacementIsSent() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductRequest request = request();

        Product updated = productService.updateProduct(3L, request);

        assertEquals("New name", updated.getName());
        assertEquals("New description", updated.getDescription());
        assertEquals(new BigDecimal("25.50"), updated.getPrice());
        assertEquals("Tunics", updated.getCategory());
        assertEquals("Green", updated.getColor());
        assertEquals(8, updated.getStock());
        assertEquals("/uploads/products/old.jpg", updated.getImageUrl());
    }

    @Test
    void acceptsReplacementImageUrl() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductRequest request = request();
        request.setImageUrl("/uploads/products/new.png");

        assertEquals(
                "/uploads/products/new.png",
                productService.updateProduct(3L, request).getImageUrl()
        );
    }

    @Test
    void softDeleteSetsActiveFalseWithoutDeletingTheProduct() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(existing));

        productService.softDeleteProduct(3L);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertFalse(captor.getValue().getActive());
    }

    @Test
    void editAndDeleteReturnNotFoundForMissingProducts() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.updateProduct(999L, request()));
        assertThrows(ResourceNotFoundException.class,
                () -> productService.softDeleteProduct(999L));
    }

    private ProductRequest request() {
        ProductRequest request = new ProductRequest();
        request.setName("New name");
        request.setDescription("New description");
        request.setPrice(new BigDecimal("25.50"));
        request.setCategory("Tunics");
        request.setColor("Green");
        request.setStock(8);
        return request;
    }
}
