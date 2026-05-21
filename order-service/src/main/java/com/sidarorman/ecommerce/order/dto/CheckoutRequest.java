package com.sidarorman.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutRequest {

    @NotEmpty(message = "Checkout items cannot be empty")
    @Valid
    private List<CheckoutItem> items;

    @NotBlank(message = "Card number is required")
    private String cardNumber;

    @NotBlank(message = "CVV is required")
    private String cvv;

    @NotBlank(message = "Expiry date is required")
    private String expiryDate; // e.g. "12/29"
}
