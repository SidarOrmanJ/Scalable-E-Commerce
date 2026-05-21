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
public class CatalogClient {

    private final RestTemplate restTemplate;

    @Value("${services.stock.url}")
    private String stockServiceUrl;

    public ProductDto getProduct(String productId) {
        String url = stockServiceUrl + "/api/stock/products/" + productId;
        log.info("Fetching product catalog price from {} for product {}", url, productId);
        try {
            return restTemplate.getForObject(url, ProductDto.class);
        } catch (Exception e) {
            log.error("Failed to fetch product catalog info: {}", e.getMessage());
            throw new RuntimeException("Could not retrieve product pricing for product: " + productId);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductDto {
        private String id;
        private String name;
        private BigDecimal price;
        private Integer quantity;
    }
}
