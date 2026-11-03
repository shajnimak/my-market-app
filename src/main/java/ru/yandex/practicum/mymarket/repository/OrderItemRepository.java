package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import ru.yandex.practicum.mymarket.model.OrderItem;

import java.util.Collection;

public interface OrderItemRepository extends R2dbcRepository<OrderItem, Long> {

    Flux<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    Flux<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);
}
