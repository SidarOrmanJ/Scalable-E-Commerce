package com.sidarorman.ecommerce.payment.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@Slf4j
public class PaymentController {

    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        log.info("Received payment processing request for order {} with amount {}", request.getOrderId(), request.getAmount());

        // Simple mock rule: cards starting with "4000" will fail (insufficient funds mock)
        if (request.getCardNumber() != null && request.getCardNumber().startsWith("4000")) {
            log.warn("Payment declined for order {} due to card failure simulation (starts with 4000)", request.getOrderId());
            PaymentResponse response = PaymentResponse.builder()
                    .success(false)
                    .message("Declined: Insufficient funds or invalid card status.")
                    .build();
            return ResponseEntity.ok(response);
        }

        String transactionId = UUID.randomUUID().toString();
        log.info("Payment succeeded for order {}. Transaction ID: {}", request.getOrderId(), transactionId);

        PaymentResponse response = PaymentResponse.builder()
                .success(true)
                .transactionId(transactionId)
                .message("Payment approved.")
                .build();

        return ResponseEntity.ok(response);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentRequest {
        private Long orderId;
        private BigDecimal amount;
        private String cardNumber;
        private String cvv;
        private String expiryDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentResponse {
        private boolean success;
        private String transactionId;
        private String message;
    }
}
