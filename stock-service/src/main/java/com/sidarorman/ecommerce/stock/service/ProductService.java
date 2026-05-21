package com.sidarorman.ecommerce.stock.service;

import com.sidarorman.ecommerce.grpc.OrderItem;
import com.sidarorman.ecommerce.stock.exception.InsufficientStockException;
import com.sidarorman.ecommerce.stock.exception.ProductNotFoundException;
import com.sidarorman.ecommerce.stock.model.Product;
import com.sidarorman.ecommerce.stock.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public Product getProduct(String id) {
        log.info("Fetching product {} from database...", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with ID " + id + " not found"));
    }

    @Transactional
    public Product createProduct(Product product) {
        log.info("Creating product {}", product.getId());
        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public Product updateProduct(String id, Product updatedProduct) {
        log.info("Updating product {} in database, evicting cache...", id);
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with ID " + id + " not found"));

        existing.setName(updatedProduct.getName());
        existing.setPrice(updatedProduct.getPrice());
        existing.setQuantity(updatedProduct.getQuantity());

        return productRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(String id) {
        log.info("Deleting product {} in database, evicting cache...", id);
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException("Product with ID " + id + " not found");
        }
        productRepository.deleteById(id);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deductStockWithLock(String id, int quantity) {
        log.info("Acquiring lock to deduct {} units from product {}, evicting cache...", quantity, id);
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

        if (product.getQuantity() < quantity) {
            throw new InsufficientStockException("Insufficient stock for product " + id + ". Required: " + quantity + ", Available: " + product.getQuantity());
        }

        product.setQuantity(product.getQuantity() - quantity);
        productRepository.save(product);
        log.info("Successfully deducted stock for product {}. Remaining: {}", id, product.getQuantity());
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void releaseStockWithLock(String id, int quantity) {
        log.info("Acquiring lock to release {} units for product {}, evicting cache...", quantity, id);
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

        product.setQuantity(product.getQuantity() + quantity);
        productRepository.save(product);
        log.info("Successfully released stock for product {}. New quantity: {}", id, product.getQuantity());
    }

    @Transactional
    public void deductStockWithLockBulk(List<OrderItem> items) {
        log.info("Acquiring locks and executing bulk stock deduction for {} items...", items.size());
        Cache cache = cacheManager.getCache("products");
        for (OrderItem item : items) {
            String id = item.getProductId();
            int quantity = item.getQuantity();
            
            Product product = productRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

            if (product.getQuantity() < quantity) {
                throw new InsufficientStockException("Insufficient stock for product " + id + 
                        ". Required: " + quantity + ", Available: " + product.getQuantity());
            }

            product.setQuantity(product.getQuantity() - quantity);
            productRepository.save(product);
            log.info("Successfully deducted stock for product {}. Remaining: {}", id, product.getQuantity());

            if (cache != null) {
                cache.evict(id);
                log.info("Evicted product {} from cache", id);
            }
        }
    }

    @Transactional
    public void releaseStockWithLockBulk(List<OrderItem> items) {
        log.info("Acquiring locks and executing bulk stock release for {} items...", items.size());
        Cache cache = cacheManager.getCache("products");
        for (OrderItem item : items) {
            String id = item.getProductId();
            int quantity = item.getQuantity();
            
            Product product = productRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ProductNotFoundException("Product " + id + " not found"));

            product.setQuantity(product.getQuantity() + quantity);
            productRepository.save(product);
            log.info("Successfully released stock for product {}. New quantity: {}", id, product.getQuantity());

            if (cache != null) {
                cache.evict(id);
                log.info("Evicted product {} from cache", id);
            }
        }
    }
}
