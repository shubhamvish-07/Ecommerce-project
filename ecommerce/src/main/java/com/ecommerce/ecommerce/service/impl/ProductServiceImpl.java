package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.dto.ProductRequest;
import com.ecommerce.ecommerce.entity.Product;
import com.ecommerce.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.ecommerce.repository.ProductRepository;
import com.ecommerce.ecommerce.service.ProductService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductServiceImpl implements ProductService {


    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public List<Product> getAllProducts() {
        return productRepository.findByActiveTrue();
    }

    @Override
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategoryAndActiveTrue(category);
    }

    @Override
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .filter(Product::getActive)
                .orElseThrow(() ->
                        new RuntimeException("Product not found")
                );
    }

    @Override
    public Product createProduct(ProductRequest request) {

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .color(request.getColor())
                .imageUrl(request.getImageUrl())
                .stock(request.getStock())
                .active(true)
                .build();

        return productRepository.save(product);
    }

    @Override
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .filter(Product::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setColor(request.getColor());
        product.setStock(request.getStock());
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }

        return productRepository.save(product);
    }

    @Override
    public void softDeleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        product.setActive(false);
        productRepository.save(product);
    }

}
