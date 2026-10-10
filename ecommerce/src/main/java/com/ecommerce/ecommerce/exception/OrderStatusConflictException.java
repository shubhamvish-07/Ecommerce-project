package com.ecommerce.ecommerce.exception;

public class OrderStatusConflictException extends RuntimeException {

    public OrderStatusConflictException(String message) {
        super(message);
    }
}
