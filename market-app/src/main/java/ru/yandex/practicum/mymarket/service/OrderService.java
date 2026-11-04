package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.exception.OrderNotFoundException;
import ru.yandex.practicum.mymarket.mapper.OrderMapper;
import ru.yandex.practicum.mymarket.model.CartLine;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.clock = clock;
    }

    /**
     * Все заказы, новые первыми. Позиции загружаются одним запросом для всех заказов.
     */
    public Flux<OrderDto> getOrders() {
        return orderRepository.findAllByOrderByIdDesc()
                .collectList()
                .flatMapMany(orders -> loadItems(orders)
                        .flatMapIterable(itemsByOrder -> orders.stream()
                                .map(order -> OrderMapper.toDto(order,
                                        List.copyOf(itemsByOrder.getOrDefault(order.getId(), List.of()))))
                                .toList()));
    }

    public Mono<OrderDto> getOrder(long id) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(id)))
                .flatMap(order -> orderItemRepository.findByOrderIdOrderByIdAsc(id)
                        .collectList()
                        .map(orderItems -> OrderMapper.toDto(order, orderItems)));
    }

    @Transactional
    public Mono<Long> createOrder(List<CartLine> cartLines) {
        long totalSum = CartLine.total(cartLines);
        return orderRepository.save(new Order(LocalDateTime.now(clock), totalSum))
                .flatMap(order -> orderItemRepository
                        .saveAll(cartLines.stream().map(line -> new OrderItem(order.getId(), line)).toList())
                        .then(Mono.just(order.getId())));
    }

    private Mono<Map<Long, Collection<OrderItem>>> loadItems(List<Order> orders) {
        if (orders.isEmpty()) {
            return Mono.just(Map.of());
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        return orderItemRepository.findByOrderIdInOrderByIdAsc(orderIds)
                .collectMultimap(OrderItem::getOrderId);
    }
}
