package com.sidarorman.ecommerce.order.repository;

import com.sidarorman.ecommerce.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
