package com.sidarorman.ecommerce.order.controller;

import com.sidarorman.ecommerce.order.dto.CheckoutRequest;
import com.sidarorman.ecommerce.order.model.Order;
import com.sidarorman.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CheckoutRequest request) {
        Order completedOrder = orderService.checkout(username, request);
        return new ResponseEntity<>(completedOrder, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}
