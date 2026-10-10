package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.entity.CustomerOrder;
import com.ecommerce.ecommerce.entity.OrderItem;
import com.ecommerce.ecommerce.entity.Product;
import com.ecommerce.ecommerce.entity.User;
import com.ecommerce.ecommerce.enums.OrderStatus;
import com.ecommerce.ecommerce.exception.OrderStatusConflictException;
import com.ecommerce.ecommerce.repository.OrderRepository;
import com.ecommerce.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    private AdminOrderServiceImpl adminOrderService;
    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        adminOrderService = new AdminOrderServiceImpl(orderRepository, productRepository);
        user = User.builder().id(7L).name("Customer").email("customer@example.com").build();
        product = Product.builder().id(3L).name("Woven Shirt").stock(4).build();
    }

    @Test
    void allowsPlacedToShipped() {
        assertAllowed(OrderStatus.PLACED, OrderStatus.SHIPPED);
    }

    @Test
    void allowsShippedToDelivered() {
        assertAllowed(OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    }

    @Test
    void allowsPlacedToCancelledAndRestoresStockOnce() {
        CustomerOrder order = order(OrderStatus.PLACED);
        when(orderRepository.findAdminOrderById(11L)).thenReturn(Optional.of(order));
        when(orderRepository.updateStatusIfCurrent(11L, OrderStatus.PLACED, OrderStatus.CANCELLED))
                .thenReturn(1);
        when(productRepository.restoreStockIfWithinRange(3L, 2, Integer.MAX_VALUE - 2))
                .thenReturn(1);

        assertEquals(OrderStatus.CANCELLED,
                adminOrderService.updateStatus(11L, OrderStatus.CANCELLED).getStatus());

        verify(productRepository).restoreStockIfWithinRange(3L, 2, Integer.MAX_VALUE - 2);
    }

    @Test
    void rejectsEveryForbiddenTransition() {
        for (OrderStatus current : OrderStatus.values()) {
            for (OrderStatus requested : OrderStatus.values()) {
                if (isAllowed(current, requested)) {
                    continue;
                }
                clearInvocations(orderRepository, productRepository);
                CustomerOrder order = order(current);
                when(orderRepository.findAdminOrderById(11L)).thenReturn(Optional.of(order));

                assertThrows(
                        OrderStatusConflictException.class,
                        () -> adminOrderService.updateStatus(11L, requested),
                        current + " to " + requested
                );
                verify(orderRepository, org.mockito.Mockito.never())
                        .updateStatusIfCurrent(11L, current, requested);
                verifyNoInteractions(productRepository);
            }
        }
    }

    @Test
    void rejectsConcurrentStatusChange() {
        when(orderRepository.findAdminOrderById(11L))
                .thenReturn(Optional.of(order(OrderStatus.PLACED)));
        when(orderRepository.updateStatusIfCurrent(11L, OrderStatus.PLACED, OrderStatus.SHIPPED))
                .thenReturn(0);

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(11L, OrderStatus.SHIPPED)
        );
        verifyNoInteractions(productRepository);
    }

    private void assertAllowed(OrderStatus current, OrderStatus requested) {
        when(orderRepository.findAdminOrderById(11L))
                .thenReturn(Optional.of(order(current)));
        when(orderRepository.updateStatusIfCurrent(11L, current, requested)).thenReturn(1);

        assertEquals(requested, adminOrderService.updateStatus(11L, requested).getStatus());
        verify(orderRepository).updateStatusIfCurrent(11L, current, requested);
        verifyNoInteractions(productRepository);
    }

    private CustomerOrder order(OrderStatus status) {
        OrderItem item = OrderItem.builder()
                .id(5L)
                .product(product)
                .productName("Woven Shirt")
                .unitPrice(new BigDecimal("10.00"))
                .quantity(2)
                .lineTotal(new BigDecimal("20.00"))
                .build();
        return CustomerOrder.builder()
                .id(11L)
                .user(user)
                .status(status)
                .totalAmount(new BigDecimal("20.00"))
                .items(List.of(item))
                .build();
    }

    private boolean isAllowed(OrderStatus current, OrderStatus requested) {
        return current == OrderStatus.PLACED
                && (requested == OrderStatus.SHIPPED || requested == OrderStatus.CANCELLED)
                || current == OrderStatus.SHIPPED && requested == OrderStatus.DELIVERED;
    }
}
