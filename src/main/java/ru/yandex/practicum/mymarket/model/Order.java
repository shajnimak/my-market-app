package ru.yandex.practicum.mymarket.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("orders")
public class Order {

    @Id
    private Long id;

    private LocalDateTime createdAt;

    private long totalSum;

    protected Order() {
    }

    public Order(LocalDateTime createdAt, long totalSum) {
        this.createdAt = createdAt;
        this.totalSum = totalSum;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getTotalSum() {
        return totalSum;
    }
}
