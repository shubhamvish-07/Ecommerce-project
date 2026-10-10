package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.dto.CreateOrderRequest;
import com.ecommerce.ecommerce.dto.DeliveryAddressRequest;
import com.ecommerce.ecommerce.dto.OrderItemResponse;
import com.ecommerce.ecommerce.dto.OrderLineRequest;
import com.ecommerce.ecommerce.dto.OrderResponse;
import com.ecommerce.ecommerce.entity.CustomerOrder;
import com.ecommerce.ecommerce.entity.OrderItem;
import com.ecommerce.ecommerce.entity.Product;
import com.ecommerce.ecommerce.entity.User;
import com.ecommerce.ecommerce.enums.OrderStatus;
import com.ecommerce.ecommerce.enums.PaymentMethod;
import com.ecommerce.ecommerce.exception.InsufficientStockException;
import com.ecommerce.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.ecommerce.repository.OrderRepository;
import com.ecommerce.ecommerce.repository.ProductRepository;
import com.ecommerce.ecommerce.repository.UserRepository;
import com.ecommerce.ecommerce.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderServiceImpl(
            UserRepository userRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(String email, CreateOrderRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Map<Long, Long> quantitiesByProductId = new LinkedHashMap<>();
        Map<ProductSize, Long> quantitiesByProductAndSize = new LinkedHashMap<>();
        for (OrderLineRequest item : request.getItems()) {
            quantitiesByProductId.merge(
                    item.getProductId(),
                    item.getQuantity().longValue(),
                    Long::sum
            );
            quantitiesByProductAndSize.merge(
                    new ProductSize(item.getProductId(), item.getSize().trim()),
                    item.getQuantity().longValue(),
                    Long::sum
            );
        }

        Map<Long, Product> productsById = productRepository
                .findAllByIdIn(quantitiesByProductId.keySet())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        CustomerOrder order = CustomerOrder.builder()
                .user(user)
                .status(OrderStatus.PLACED)
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();
        DeliveryAddressRequest address = request.getDeliveryAddress();
        order.setRecipientName(address.getName().trim());
        order.setRecipientPhone(address.getPhone().trim());
        order.setDeliveryAddress(address.getAddressLine().trim());
        order.setDeliveryCity(address.getCity().trim());
        order.setDeliveryState(address.getState().trim());
        order.setDeliveryPostalCode(address.getPostalCode().trim());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Long> requested : quantitiesByProductId.entrySet()) {
            Product product = productsById.get(requested.getKey());
            if (product == null || !Boolean.TRUE.equals(product.getActive())) {
                throw new ResourceNotFoundException("Product not found");
            }

            int availableStock = product.getStock() == null ? 0 : product.getStock();
            long requestedQuantity = requested.getValue();
            if (requestedQuantity > Integer.MAX_VALUE
                    || productRepository.decrementStockIfAvailable(
                    product.getId(), (int) requestedQuantity) == 0) {
                Integer currentStock = requestedQuantity <= Integer.MAX_VALUE
                        ? productRepository.findStockForUpdate(product.getId())
                        : availableStock;
                int remainingStock = currentStock == null ? 0 : currentStock;
                throw new InsufficientStockException(
                        "Insufficient stock for " + product.getName()
                                + ": " + remainingStock + " left"
                );
            }

        }

        for (Map.Entry<ProductSize, Long> requested : quantitiesByProductAndSize.entrySet()) {
            Product product = productsById.get(requested.getKey().productId());
            long requestedQuantity = requested.getValue();
            int quantity = (int) requestedQuantity;
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(requestedQuantity));
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .size(requested.getKey().size())
                    .unitPrice(product.getPrice())
                    .quantity(quantity)
                    .lineTotal(lineTotal)
                    .build();

            order.getItems().add(orderItem);
            totalAmount = totalAmount.add(lineTotal);
        }

        order.setTotalAmount(totalAmount);

        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return orderRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CustomerOrder order = orderRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        return toResponse(order);
    }

    private OrderResponse toResponse(CustomerOrder order) {
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

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
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

    private record ProductSize(Long productId, String size) {}
}
