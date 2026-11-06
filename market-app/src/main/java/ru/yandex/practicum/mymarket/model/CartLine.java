package ru.yandex.practicum.mymarket.model;

import java.util.List;

/**
 * Строка корзины вместе с данными товара: количество берётся из {@code cart_items},
 * название, описание и цена — из карточки товара в кеше.
 */
public record CartLine(Long itemId, String title, String description, long price, int quantity) {

    public long totalPrice() {
        return price * quantity;
    }

    public static long total(List<CartLine> cartLines) {
        return cartLines.stream().mapToLong(CartLine::totalPrice).sum();
    }
}
