package com.sidarorman.ecommerce.order.grpc;

import com.sidarorman.ecommerce.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class InventoryClient {

    private final String host;
    private final int port;
    private ManagedChannel channel;
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    public InventoryClient(
            @Value("${grpc.stock-service.host:localhost}") String host,
            @Value("${grpc.stock-service.port:9090}") int port) {
        this.host = host;
        this.port = port;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing gRPC inventory client targeting {}:{}", host, port);
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = InventoryServiceGrpc.newBlockingStub(channel);
    }

    public boolean deductStock(List<com.sidarorman.ecommerce.order.model.OrderItemEntity> items) {
        log.info("Sending gRPC deduct stock request for {} items", items.size());
        
        List<OrderItem> protoItems = new ArrayList<>();
        for (com.sidarorman.ecommerce.order.model.OrderItemEntity item : items) {
            protoItems.add(OrderItem.newBuilder()
                    .setProductId(item.getProductId())
                    .setQuantity(item.getQuantity())
                    .build());
        }

        DeductStockRequest request = DeductStockRequest.newBuilder()
                .addAllItems(protoItems)
                .build();

        try {
            DeductStockResponse response = stub.checkAndDeductStock(request);
            if (!response.getSuccess()) {
                log.warn("Deduct stock failed: {}", response.getMessage());
                return false;
            }
            log.info("Deduct stock succeeded: {}", response.getMessage());
            return true;
        } catch (Exception e) {
            log.error("gRPC error occurred while deducting stock: {}", e.getMessage());
            return false;
        }
    }

    public void releaseStock(List<com.sidarorman.ecommerce.order.model.OrderItemEntity> items) {
        log.info("Sending gRPC release stock request for {} items", items.size());
        
        List<OrderItem> protoItems = new ArrayList<>();
        for (com.sidarorman.ecommerce.order.model.OrderItemEntity item : items) {
            protoItems.add(OrderItem.newBuilder()
                    .setProductId(item.getProductId())
                    .setQuantity(item.getQuantity())
                    .build());
        }

        ReleaseStockRequest request = ReleaseStockRequest.newBuilder()
                .addAllItems(protoItems)
                .build();

        try {
            ReleaseStockResponse response = stub.releaseStock(request);
            log.info("Release stock response: success={}, message={}", response.getSuccess(), response.getMessage());
        } catch (Exception e) {
            log.error("gRPC error occurred while releasing stock: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
