package com.sidarorman.ecommerce.notification.service;

import com.sidarorman.ecommerce.notification.event.OrderItemInfo;
import com.sidarorman.ecommerce.notification.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public void generateAndUploadInvoice(OrderPlacedEvent event) {
        log.info("Generating invoice for order #{}...", event.getOrderId());
        
        StringBuilder sb = new StringBuilder();
        sb.append("=========================================\n");
        sb.append("         E-COMMERCE ORDER INVOICE        \n");
        sb.append("=========================================\n");
        sb.append("Order ID:    ").append(event.getOrderId()).append("\n");
        sb.append("Customer:    ").append(event.getUsername()).append("\n");
        sb.append("Date:        ").append(event.getTimestamp()).append("\n");
        sb.append("-----------------------------------------\n");
        sb.append("Items Purchased:\n");
        
        for (OrderItemInfo item : event.getItems()) {
            sb.append("  - Product ID: ").append(item.getProductId())
              .append(" | Qty: ").append(item.getQuantity())
              .append(" | Price: $").append(item.getPrice()).append("\n");
        }
        
        sb.append("-----------------------------------------\n");
        sb.append("Total Amount: $").append(event.getTotalAmount()).append("\n");
        sb.append("=========================================\n");
        sb.append("Thank you for shopping with us!\n");

        String invoiceContent = sb.toString();
        String fileName = "invoice_" + event.getOrderId() + ".txt";

        ensureBucketExists();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromString(invoiceContent));
            log.info("Invoice '{}' uploaded successfully to S3 bucket '{}'.", fileName, bucketName);
        } catch (Exception e) {
            log.error("Failed to upload invoice to S3: {}", e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            log.info("S3 bucket '{}' does not exist. Creating it...", bucketName);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            log.info("S3 bucket '{}' created successfully.", bucketName);
        } catch (Exception e) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("S3 bucket '{}' created successfully (via fallback create).", bucketName);
            } catch (Exception ex) {
                log.error("Failed to verify/create S3 bucket: {}", ex.getMessage());
            }
        }
    }
}
