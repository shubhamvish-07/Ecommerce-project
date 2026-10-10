package com.ecommerce.ecommerce.service;

import com.ecommerce.ecommerce.dto.CreateOrderRequest;
import com.ecommerce.ecommerce.dto.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse placeOrder(String email, CreateOrderRequest request);

    List<OrderResponse> getOrders(String email);

    OrderResponse getOrder(String email, Long id);
}
