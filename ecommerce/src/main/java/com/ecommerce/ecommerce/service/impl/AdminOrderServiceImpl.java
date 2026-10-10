package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.dto.AdminOrderResponse;
import com.ecommerce.ecommerce.dto.OrderItemResponse;
import com.ecommerce.ecommerce.entity.CustomerOrder;
import com.ecommerce.ecommerce.entity.OrderItem;
import com.ecommerce.ecommerce.enums.OrderStatus;
import com.ecommerce.ecommerce.exception.OrderStatusConflictException;
import com.ecommerce.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.ecommerce.repository.OrderRepository;
import com.ecommerce.ecommerce.repository.ProductRepository;
import com.ecommerce.ecommerce.service.AdminOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public AdminOrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminOrderResponse> getOrders(OrderStatus status) {
        List<CustomerOrder> orders = status == null
                ? orderRepository.findAllByOrderByCreatedAtDescIdDesc()
                : orderRepository.findByStatusOrderByCreatedAtDescIdDesc(status);
        return orders.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderResponse getOrder(Long id) {
        return toResponse(findOrder(id));
    }

    @Override
    @Transactional
    public AdminOrderResponse updateStatus(Long id, OrderStatus newStatus) {
        CustomerOrder order = findOrder(id);
        OrderStatus currentStatus = order.getStatus();
        if (!isAllowedTransition(currentStatus, newStatus)) {
            throw new OrderStatusConflictException(
                    "Order cannot transition from " + currentStatus + " to " + newStatus
            );
        }

        if (orderRepository.updateStatusIfCurrent(id, currentStatus, newStatus) != 1) {
            throw new OrderStatusConflictException(
                    "Order status changed before this update could be applied"
            );
        }

        if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                int quantity = item.getQuantity();
                int maximumStock = Integer.MAX_VALUE - quantity;
                if (productRepository.restoreStockIfWithinRange(
                        item.getProduct().getId(), quantity, maximumStock) != 1) {
                    throw new OrderStatusConflictException(
                            "Unable to restore stock for " + item.getProductName()
                    );
                }
            }
        }

        order.setStatus(newStatus);
        return toResponse(order);
    }

    private CustomerOrder findOrder(Long id) {
        return orderRepository.findAdminOrderById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private boolean isAllowedTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        return currentStatus == OrderStatus.PLACED
                && (newStatus == OrderStatus.SHIPPED || newStatus == OrderStatus.CANCELLED)
                || currentStatus == OrderStatus.SHIPPED && newStatus == OrderStatus.DELIVERED;
    }

    private AdminOrderResponse toResponse(CustomerOrder order) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(item -> new OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getSize(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineTotal()
                ))
                .toList();

        return new AdminOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUser().getName(),
                order.getUser().getEmail(),
                order.getPaymentMethod(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getDeliveryAddress(),
                order.getDeliveryCity(),
                order.getDeliveryState(),
                order.getDeliveryPostalCode(),
                items
        );
    }
}
