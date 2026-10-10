package com.ecommerce.ecommerce.service;

import com.ecommerce.ecommerce.dto.ProductRequest;
import com.ecommerce.ecommerce.entity.Product;

import java.util.List;

public interface ProductService {


    List<Product> getAllProducts();

    List<Product> getProductsByCategory(String category);

    Product getProductById(Long id);

    Product createProduct(ProductRequest request);

    Product updateProduct(Long id, ProductRequest request);

    void softDeleteProduct(Long id);

}
