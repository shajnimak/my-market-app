package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.exception.OrderNotFoundException;
import ru.yandex.practicum.mymarket.mapper.OrderMapper;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    public List<OrderDto> getOrders() {
        return orderRepository.findAllByOrderByIdDesc().stream()
                .map(OrderMapper::toDto)
                .toList();
    }

    public OrderDto getOrder(long id) {
        return orderRepository.findWithItemsById(id)
                .map(OrderMapper::toDto)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional
    public long createOrder(List<CartItem> cartItems) {
        Order order = new Order(LocalDateTime.now(clock));
        cartItems.forEach(cartItem -> order.addItem(new OrderItem(cartItem.getItem(), cartItem.getQuantity())));
        return orderRepository.save(order).getId();
    }
}
