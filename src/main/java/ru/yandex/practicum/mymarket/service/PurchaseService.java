package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.model.CartItem;

import java.util.List;

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
    public long buy() {
        List<CartItem> cartItems = cartService.getCartItems();
        if (cartItems.isEmpty()) {
            throw new EmptyCartException();
        }
        long orderId = orderService.createOrder(cartItems);
        cartService.clear();
        return orderId;
    }
}
