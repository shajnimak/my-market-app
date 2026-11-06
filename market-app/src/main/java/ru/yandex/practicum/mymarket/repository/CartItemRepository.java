package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.CartItem;

import java.util.Collection;

public interface CartItemRepository extends R2dbcRepository<CartItem, Long> {

    Mono<CartItem> findByItemId(Long itemId);

    Flux<CartItem> findByItemIdIn(Collection<Long> itemIds);

    /**
     * Позиции корзины в порядке добавления. Данные товаров для них берутся из кеша каталога.
     */
    Flux<CartItem> findAllByOrderByIdAsc();
}
