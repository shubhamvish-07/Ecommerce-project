package com.ecommerce.ecommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateOrderRequest {

    @NotEmpty(message = "At least one order item is required")
    @Size(max = 100, message = "An order cannot contain more than 100 lines")
    private List<@Valid OrderLineRequest> items;

    @Valid
    @NotNull(message = "A delivery address is required")
    private DeliveryAddressRequest deliveryAddress;
}
