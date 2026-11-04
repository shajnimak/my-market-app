package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.CartLine;

import java.util.Collection;

public interface CartItemRepository extends R2dbcRepository<CartItem, Long> {

    Mono<CartItem> findByItemId(Long itemId);

    Flux<CartItem> findByItemIdIn(Collection<Long> itemIds);

    @Query("""
            select ci.item_id, i.title, i.description, i.price, ci.quantity
            from cart_items ci
                     join items i on i.id = ci.item_id
            order by ci.id
            """)
    Flux<CartLine> findCartLines();
}
