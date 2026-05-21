package com.sidarorman.ecommerce.order.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentClient {

    private final RestTemplate restTemplate;

    @Value("${services.payment.url}")
    private String paymentServiceUrl;

    public PaymentResponse processPayment(Long orderId, BigDecimal amount, String cardNumber, String cvv, String expiryDate) {
        String url = paymentServiceUrl + "/api/payments/process";
        log.info("Invoking payment REST endpoint {} for order {}", url, orderId);

        PaymentRequest request = PaymentRequest.builder()
                .orderId(orderId)
                .amount(amount)
                .cardNumber(cardNumber)
                .cvv(cvv)
                .expiryDate(expiryDate)
                .build();

        try {
            return restTemplate.postForObject(url, request, PaymentResponse.class);
        } catch (Exception e) {
            log.error("Payment request failed: {}", e.getMessage());
            return PaymentResponse.builder()
                    .success(false)
                    .message("Failed to connect to payment service: " + e.getMessage())
                    .build();
        }
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
