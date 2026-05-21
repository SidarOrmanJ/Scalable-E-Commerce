package com.sidarorman.ecommerce.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemInfo {
    private String productId;
    private Integer quantity;
    private BigDecimal price;
}
