package ru.yandex.practicum.mymarket.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Заказ покупателя {@code userId}.
 */
@Table("orders")
public class Order {

    @Id
    private Long id;

    private Long userId;

    private LocalDateTime createdAt;

    private long totalSum;

    protected Order() {
    }

    public Order(Long userId, LocalDateTime createdAt, long totalSum) {
        this.userId = userId;
        this.createdAt = createdAt;
        this.totalSum = totalSum;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getTotalSum() {
        return totalSum;
    }
}
