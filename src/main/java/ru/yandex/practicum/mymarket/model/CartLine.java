package ru.yandex.practicum.mymarket.model;

/**
 * Строка корзины вместе с данными товара — результат соединения {@code cart_items} и {@code items}.
 */
public record CartLine(Long itemId, String title, String description, long price, int quantity) {

    public long totalPrice() {
        return price * quantity;
    }
}
