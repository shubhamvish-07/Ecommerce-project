package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.dto.CreateOrderRequest;
import com.ecommerce.ecommerce.dto.DeliveryAddressRequest;
import com.ecommerce.ecommerce.dto.OrderLineRequest;
import com.ecommerce.ecommerce.dto.OrderResponse;
import com.ecommerce.ecommerce.entity.CustomerOrder;
import com.ecommerce.ecommerce.entity.Product;
import com.ecommerce.ecommerce.entity.User;
import com.ecommerce.ecommerce.enums.OrderStatus;
import com.ecommerce.ecommerce.enums.PaymentMethod;
import com.ecommerce.ecommerce.exception.InsufficientStockException;
import com.ecommerce.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.ecommerce.repository.OrderRepository;
import com.ecommerce.ecommerce.repository.ProductRepository;
import com.ecommerce.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(7L)
                .email("customer@example.com")
                .build();
    }

    @Test
    void mergesDuplicateLinesAndUsesDatabasePricesForTotal() {
        Product product = product(3L, "Handloom Shirt", "12.50", 10, true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(product));
        when(productRepository.decrementStockIfAvailable(3L, 5)).thenReturn(1);
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(
                user.getEmail(),
                request(line(3L, 2), line(3L, 3))
        );

        assertEquals(OrderStatus.PLACED, response.getStatus());
        assertEquals(PaymentMethod.COD, response.getPaymentMethod());
        assertEquals("12 Weaver Lane", response.getDeliveryAddress());
        assertEquals(new BigDecimal("62.50"), response.getTotalAmount());
        assertEquals(1, response.getItems().size());
        assertEquals(5, response.getItems().getFirst().getQuantity());
        assertEquals("Handloom Shirt", response.getItems().getFirst().getProductName());
        assertEquals("M", response.getItems().getFirst().getSize());
        assertEquals(new BigDecimal("12.50"), response.getItems().getFirst().getUnitPrice());
        assertEquals(new BigDecimal("62.50"), response.getItems().getFirst().getLineTotal());

        ArgumentCaptor<CustomerOrder> orderCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(user, orderCaptor.getValue().getUser());
        assertEquals(1, orderCaptor.getValue().getItems().size());
        verify(productRepository).decrementStockIfAvailable(3L, 5);
    }

    @Test
    void keepsDifferentSelectedSizesAsSeparateOrderLinesButUsesSharedStock() {
        Product product = product(3L, "Handloom Shirt", "12.50", 10, true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(product));
        when(productRepository.decrementStockIfAvailable(3L, 5)).thenReturn(1);
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(
                user.getEmail(),
                request(line(3L, 2, "S"), line(3L, 3, "L"))
        );

        assertEquals(2, response.getItems().size());
        assertEquals("S", response.getItems().get(0).getSize());
        assertEquals(2, response.getItems().get(0).getQuantity());
        assertEquals("L", response.getItems().get(1).getSize());
        assertEquals(3, response.getItems().get(1).getQuantity());
        verify(productRepository).decrementStockIfAvailable(3L, 5);
    }

    @Test
    void rejectsInactiveProduct() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findAllByIdIn(anyCollection()))
                .thenReturn(List.of(product(3L, "Inactive Shirt", "12.50", 10, false)));

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.placeOrder(user.getEmail(), request(line(3L, 1)))
        );

        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void rejectsUnknownProduct() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.placeOrder(user.getEmail(), request(line(999L, 1)))
        );

        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void rejectsQuantityAboveStockAfterMergingDuplicateLines() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findAllByIdIn(anyCollection()))
                .thenReturn(List.of(product(3L, "Handloom Shirt", "12.50", 5, true)));
        when(productRepository.decrementStockIfAvailable(3L, 6)).thenReturn(0);
        when(productRepository.findStockForUpdate(3L)).thenReturn(5);

        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class,
                () -> orderService.placeOrder(
                        user.getEmail(),
                        request(line(3L, 3), line(3L, 3))
                )
        );

        assertEquals("Insufficient stock for Handloom Shirt: 5 left", exception.getMessage());
        verify(productRepository).decrementStockIfAvailable(3L, 6);
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void failedLaterLineDoesNotSaveOrderAfterEarlierStockDecrement() {
        Product first = product(3L, "First Shirt", "12.50", 10, true);
        Product second = product(4L, "Second Shirt", "9.00", 1, true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(first, second));
        when(productRepository.decrementStockIfAvailable(3L, 1)).thenReturn(1);
        when(productRepository.decrementStockIfAvailable(4L, 2)).thenReturn(0);
        when(productRepository.findStockForUpdate(4L)).thenReturn(1);

        assertThrows(
                InsufficientStockException.class,
                () -> orderService.placeOrder(
                        user.getEmail(),
                        request(line(3L, 1), line(4L, 2))
                )
        );

        verify(productRepository).decrementStockIfAvailable(3L, 1);
        verify(productRepository).decrementStockIfAvailable(4L, 2);
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void concurrentOrdersForLastUnitHaveExactlyOneWinner() throws Exception {
        Product product = product(3L, "Last Shirt", "12.50", 1, true);
        AtomicInteger stock = new AtomicInteger(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReferenceArray<Object> results = new AtomicReferenceArray<>(2);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(product));
        when(productRepository.decrementStockIfAvailable(3L, 1)).thenAnswer(invocation ->
                stock.getAndUpdate(current -> current >= 1 ? current - 1 : current) >= 1 ? 1 : 0
        );
        when(productRepository.findStockForUpdate(3L)).thenAnswer(invocation -> stock.get());
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Thread first = orderThread(ready, start, results, 0);
        Thread second = orderThread(ready, start, results, 1);
        first.start();
        second.start();
        assertEquals(true, ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        first.join(5000);
        second.join(5000);

        int successes = 0;
        int stockFailures = 0;
        for (int index = 0; index < results.length(); index++) {
            Object result = results.get(index);
            if (result instanceof OrderResponse) successes++;
            if (result instanceof InsufficientStockException) stockFailures++;
        }
        assertEquals(1, successes);
        assertEquals(1, stockFailures);
        assertEquals(0, stock.get());
        verify(orderRepository).save(any(CustomerOrder.class));
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.placeOrder(user.getEmail(), request(line(3L, 1)))
        );

        verify(productRepository, never()).findAllByIdIn(anyCollection());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void getsOrderOnlyThroughUserScopedRepositoryLookup() {
        CustomerOrder order = CustomerOrder.builder()
                .id(15L)
                .user(user)
                .status(OrderStatus.PLACED)
                .totalAmount(new BigDecimal("12.50"))
                .items(List.of())
                .build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(orderRepository.findByIdAndUser_Id(15L, user.getId())).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(user.getEmail(), 15L);

        assertEquals(15L, response.getId());
        verify(orderRepository).findByIdAndUser_Id(15L, user.getId());
        verify(orderRepository, never()).findById(anyLong());
    }

    private Product product(Long id, String name, String price, int stock, boolean active) {
        return Product.builder()
                .id(id)
                .name(name)
                .price(new BigDecimal(price))
                .stock(stock)
                .active(active)
                .build();
    }

    private CreateOrderRequest request(OrderLineRequest... items) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(items));
        DeliveryAddressRequest address = new DeliveryAddressRequest();
        address.setName("Test Customer");
        address.setPhone("9876543210");
        address.setAddressLine("12 Weaver Lane");
        address.setCity("Mumbai");
        address.setState("Maharashtra");
        address.setPostalCode("400001");
        request.setDeliveryAddress(address);
        return request;
    }

    private OrderLineRequest line(Long productId, int quantity) {
        return line(productId, quantity, "M");
    }

    private OrderLineRequest line(Long productId, int quantity, String size) {
        OrderLineRequest item = new OrderLineRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setSize(size);
        return item;
    }

    private Thread orderThread(
            CountDownLatch ready,
            CountDownLatch start,
            AtomicReferenceArray<Object> results,
            int index) {
        return new Thread(() -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    results.set(index, new IllegalStateException("Start timed out"));
                    return;
                }
                results.set(index, orderService.placeOrder(user.getEmail(), request(line(3L, 1))));
            } catch (InsufficientStockException exception) {
                results.set(index, exception);
            } catch (Throwable exception) {
                results.set(index, exception);
            }
        });
    }
}
