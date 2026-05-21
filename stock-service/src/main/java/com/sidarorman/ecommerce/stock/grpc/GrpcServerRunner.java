package com.sidarorman.ecommerce.stock.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
@RequiredArgsConstructor
@Slf4j
public class GrpcServerRunner implements CommandLineRunner {

    @Value("${grpc.server.port:9090}")
    private int port;

    private final InventoryServiceImpl inventoryService;
    private Server server;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting gRPC server on port {}...", port);
        server = ServerBuilder.forPort(port)
                .addService(inventoryService)
                .build()
                .start();
        log.info("gRPC server started successfully on port {}", port);
        
        Thread daemon = new Thread(() -> {
            try {
                server.awaitTermination();
            } catch (InterruptedException e) {
                log.warn("gRPC server termination await was interrupted");
            }
        });
        daemon.setDaemon(true);
        daemon.start();
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            log.info("Shutting down gRPC server...");
            server.shutdown();
            log.info("gRPC server shut down successfully.");
        }
    }
}
