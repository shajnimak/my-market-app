package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;

/**
 * Эмулирует покупку: переносит содержимое корзины в новый заказ и очищает корзину.
 */
@Service
public class PurchaseService {

    private final CartService cartService;
    private final OrderService orderService;

    public PurchaseService(CartService cartService, OrderService orderService) {
        this.cartService = cartService;
        this.orderService = orderService;
    }

    @Transactional
    public Mono<Long> buy() {
        return cartService.getCartLines()
                .collectList()
                .filter(cartLines -> !cartLines.isEmpty())
                .switchIfEmpty(Mono.error(EmptyCartException::new))
                .flatMap(orderService::createOrder)
                .flatMap(orderId -> cartService.clear().thenReturn(orderId));
    }
}
