package com.sidarorman.ecommerce.notification.listener;

import com.sidarorman.ecommerce.notification.event.OrderPlacedEvent;
import com.sidarorman.ecommerce.notification.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedEventListener {

    private final InvoiceService invoiceService;

    @KafkaListener(topics = "order-placed-topic", groupId = "notification-group")
    public void handleOrderPlacedEvent(OrderPlacedEvent event) {
        log.info("Received Kafka OrderPlacedEvent for order ID: {}", event.getOrderId());
        try {
            invoiceService.generateAndUploadInvoice(event);
        } catch (Exception e) {
            log.error("Error processing OrderPlacedEvent: {}", e.getMessage());
        }
    }
}
