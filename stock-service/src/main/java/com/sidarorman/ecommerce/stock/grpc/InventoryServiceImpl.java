package com.sidarorman.ecommerce.stock.grpc;

import com.sidarorman.ecommerce.grpc.DeductStockRequest;
import com.sidarorman.ecommerce.grpc.DeductStockResponse;
import com.sidarorman.ecommerce.grpc.InventoryServiceGrpc;
import com.sidarorman.ecommerce.grpc.OrderItem;
import com.sidarorman.ecommerce.grpc.ReleaseStockRequest;
import com.sidarorman.ecommerce.grpc.ReleaseStockResponse;
import com.sidarorman.ecommerce.stock.service.ProductService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final ProductService productService;

    @Override
    public void checkAndDeductStock(DeductStockRequest request, StreamObserver<DeductStockResponse> responseObserver) {
        log.info("Received gRPC request to deduct stock for {} items", request.getItemsCount());
        try {
            productService.deductStockWithLockBulk(request.getItemsList());

            DeductStockResponse response = DeductStockResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Stock deducted successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to deduct stock: {}", e.getMessage());
            DeductStockResponse response = DeductStockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void releaseStock(ReleaseStockRequest request, StreamObserver<ReleaseStockResponse> responseObserver) {
        log.info("Received gRPC request to release stock for {} items", request.getItemsCount());
        try {
            productService.releaseStockWithLockBulk(request.getItemsList());

            ReleaseStockResponse response = ReleaseStockResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Stock released successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to release stock: {}", e.getMessage());
            ReleaseStockResponse response = ReleaseStockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
