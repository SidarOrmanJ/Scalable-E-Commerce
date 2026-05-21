package com.sidarorman.ecommerce.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPlacedEvent {
    private Long orderId;
    private String username;
    private BigDecimal totalAmount;
    private List<OrderItemInfo> items;
    private String timestamp;
}
