package com.sidarorman.ecommerce.order.service;

import com.sidarorman.ecommerce.order.client.CatalogClient;
import com.sidarorman.ecommerce.order.client.PaymentClient;
import com.sidarorman.ecommerce.order.dto.CheckoutItem;
import com.sidarorman.ecommerce.order.dto.CheckoutRequest;
import com.sidarorman.ecommerce.order.event.OrderItemInfo;
import com.sidarorman.ecommerce.order.event.OrderPlacedEvent;
import com.sidarorman.ecommerce.order.grpc.InventoryClient;
import com.sidarorman.ecommerce.order.model.Order;
import com.sidarorman.ecommerce.order.model.OrderItemEntity;
import com.sidarorman.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    private static final String TOPIC = "order-placed-topic";

    @Transactional
    public Order checkout(String username, CheckoutRequest request) {
        log.info("Starting checkout orchestration for user: {}", username);

        // 1. Calculate pricing and validate catalog
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItemEntity> orderItems = new ArrayList<>();

        for (CheckoutItem item : request.getItems()) {
            CatalogClient.ProductDto product = catalogClient.getProduct(item.getProductId());
            BigDecimal itemCost = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalAmount = totalAmount.add(itemCost);

            orderItems.add(OrderItemEntity.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .price(product.getPrice())
                    .build());
        }

        // 2. Initialize Order
        Order order = Order.builder()
                .username(username)
                .status("PENDING")
                .totalAmount(totalAmount)
                .createdAt(LocalDateTime.now())
                .build();

        for (OrderItemEntity item : orderItems) {
            order.addOrderItem(item);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Initialized order {} in PENDING state", savedOrder.getId());

        // 3. Deduct Stock via gRPC
        boolean stockDeducted = inventoryClient.deductStock(savedOrder.getItems());
        if (!stockDeducted) {
            savedOrder.setStatus("FAILED");
            orderRepository.save(savedOrder);
            throw new RuntimeException("Stock reservation failed for order " + savedOrder.getId());
        }

        // 4. Process Payment via REST
        PaymentClient.PaymentResponse paymentResponse = paymentClient.processPayment(
                savedOrder.getId(),
                savedOrder.getTotalAmount(),
                request.getCardNumber(),
                request.getCvv(),
                request.getExpiryDate()
        );

        if (!paymentResponse.isSuccess()) {
            log.warn("Payment failed for order {}: {}. Initiating stock rollback...", savedOrder.getId(), paymentResponse.getMessage());
            
            // Compensation Flow: Release stock via gRPC
            inventoryClient.releaseStock(savedOrder.getItems());

            savedOrder.setStatus("FAILED");
            orderRepository.save(savedOrder);
            throw new RuntimeException("Payment processing failed: " + paymentResponse.getMessage());
        }

        // 5. Complete Order
        savedOrder.setStatus("PAID");
        Order completedOrder = orderRepository.save(savedOrder);
        log.info("Order {} processed successfully. Publishing Kafka event...", completedOrder.getId());

        // 6. Publish event to Kafka
        List<OrderItemInfo> itemInfos = new ArrayList<>();
        for (OrderItemEntity item : completedOrder.getItems()) {
            itemInfos.add(OrderItemInfo.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .build());
        }

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderId(completedOrder.getId())
                .username(completedOrder.getUsername())
                .totalAmount(completedOrder.getTotalAmount())
                .items(itemInfos)
                .timestamp(completedOrder.getCreatedAt().toString())
                .build();

        try {
            kafkaTemplate.send(TOPIC, completedOrder.getId().toString(), event);
            log.info("Kafka OrderPlacedEvent published successfully for order {}", completedOrder.getId());
        } catch (Exception e) {
            log.error("Failed to publish OrderPlacedEvent to Kafka: {}", e.getMessage());
            // In a production-ready setup, we wouldn't fail the transaction here. The order is PAID, S3/Notifications can be retried.
        }

        return completedOrder;
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        log.info("Retrieving all orders from database");
        return orderRepository.findAll();
    }
}
