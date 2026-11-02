package ru.yandex.practicum.mymarket.dto;

import java.util.List;

public record OrderDto(long id, List<OrderItemDto> items, long totalSum) {
}
