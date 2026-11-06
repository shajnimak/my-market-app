package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.CartItem;

import java.util.Collection;

/**
 * Позиции корзин. Все запросы ограничены корзиной одного покупателя ({@code userId}).
 */
public interface CartItemRepository extends R2dbcRepository<CartItem, Long> {

    Mono<CartItem> findByUserIdAndItemId(Long userId, Long itemId);

    Flux<CartItem> findByUserIdAndItemIdIn(Long userId, Collection<Long> itemIds);

    /**
     * Позиции корзины покупателя в порядке добавления. Данные товаров для них берутся из кеша каталога.
     */
    Flux<CartItem> findByUserIdOrderByIdAsc(Long userId);

    Mono<Long> countByUserId(Long userId);

    Mono<Void> deleteByUserId(Long userId);
}
