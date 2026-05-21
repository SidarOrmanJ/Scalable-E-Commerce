package com.sidarorman.ecommerce.stock.service;

import com.sidarorman.ecommerce.grpc.OrderItem;
import com.sidarorman.ecommerce.stock.exception.InsufficientStockException;
import com.sidarorman.ecommerce.stock.model.Product;
import com.sidarorman.ecommerce.stock.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "grpc.server.port=0"
})
@Testcontainers
public class ProductServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        Cache cache = cacheManager.getCache("products");
        if (cache != null) {
            cache.clear();
        }

        productA = Product.builder()
                .id("prod-A")
                .name("Product A")
                .price(BigDecimal.valueOf(10.0))
                .quantity(10)
                .build();

        productB = Product.builder()
                .id("prod-B")
                .name("Product B")
                .price(BigDecimal.valueOf(20.0))
                .quantity(5)
                .build();

        productRepository.save(productA);
        productRepository.save(productB);
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
        Cache cache = cacheManager.getCache("products");
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void testBulkDeductionSuccess() {
        // Given
        OrderItem itemA = OrderItem.newBuilder().setProductId("prod-A").setQuantity(3).build();
        OrderItem itemB = OrderItem.newBuilder().setProductId("prod-B").setQuantity(2).build();

        // When
        productService.deductStockWithLockBulk(Arrays.asList(itemA, itemB));

        // Then
        Product updatedA = productRepository.findById("prod-A").orElseThrow();
        Product updatedB = productRepository.findById("prod-B").orElseThrow();

        assertThat(updatedA.getQuantity()).isEqualTo(7);
        assertThat(updatedB.getQuantity()).isEqualTo(3);
    }

    @Test
    void testBulkDeductionFailureAndRollback() {
        // Given
        // Product A has 10 quantity, we request 3. Product B has 5 quantity, we request 6 (should fail).
        OrderItem itemA = OrderItem.newBuilder().setProductId("prod-A").setQuantity(3).build();
        OrderItem itemB = OrderItem.newBuilder().setProductId("prod-B").setQuantity(6).build();

        // When & Then
        assertThatThrownBy(() -> productService.deductStockWithLockBulk(Arrays.asList(itemA, itemB)))
                .isInstanceOf(InsufficientStockException.class);

        // Verify that the whole operation rolled back (Product A's stock is still 10, not 7!)
        Product rolledBackA = productRepository.findById("prod-A").orElseThrow();
        Product rolledBackB = productRepository.findById("prod-B").orElseThrow();

        assertThat(rolledBackA.getQuantity()).isEqualTo(10);
        assertThat(rolledBackB.getQuantity()).isEqualTo(5);
    }

    @Test
    void testBulkDeductionEvictsCache() {
        // Given
        // Populate cache by retrieving productA via productService
        productService.getProduct("prod-A");
        Cache cache = cacheManager.getCache("products");
        assertThat(cache).isNotNull();
        assertThat(cache.get("prod-A")).isNotNull();

        // When
        OrderItem itemA = OrderItem.newBuilder().setProductId("prod-A").setQuantity(2).build();
        productService.deductStockWithLockBulk(List.of(itemA));

        // Then - verify cache is evicted (should be null)
        assertThat(cache.get("prod-A")).isNull();

        // Next fetch should retrieve updated value and re-cache it
        Product updatedA = productService.getProduct("prod-A");
        assertThat(updatedA.getQuantity()).isEqualTo(8);
        assertThat(cache.get("prod-A")).isNotNull();
    }
}
