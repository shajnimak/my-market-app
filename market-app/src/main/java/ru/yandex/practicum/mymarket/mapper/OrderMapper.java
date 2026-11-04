package ru.yandex.practicum.mymarket.mapper;

import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;

import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderDto toDto(Order order, List<OrderItem> orderItems) {
        return new OrderDto(
                order.getId(),
                orderItems.stream().map(OrderMapper::toDto).toList(),
                order.getTotalSum()
        );
    }

    private static OrderItemDto toDto(OrderItem orderItem) {
        return new OrderItemDto(
                orderItem.getItemId(),
                orderItem.getTitle(),
                orderItem.getPrice(),
                orderItem.getQuantity()
        );
    }
}
