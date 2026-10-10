package com.ecommerce.ecommerce.service;

import com.ecommerce.ecommerce.dto.AdminOrderResponse;
import com.ecommerce.ecommerce.enums.OrderStatus;

import java.util.List;

public interface AdminOrderService {

    List<AdminOrderResponse> getOrders(OrderStatus status);

    AdminOrderResponse getOrder(Long id);

    AdminOrderResponse updateStatus(Long id, OrderStatus status);
}
