package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.Order;

/**
 * Заказы. Покупатель видит только свои заказы, поэтому чтение всегда идёт по {@code userId}.
 */
public interface OrderRepository extends R2dbcRepository<Order, Long> {

    Flux<Order> findByUserIdOrderByIdDesc(Long userId);

    Mono<Order> findByIdAndUserId(Long id, Long userId);
}
