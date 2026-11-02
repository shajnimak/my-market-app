package ru.yandex.practicum.mymarket.exception;

public class OrderNotFoundException extends NotFoundException {

    public OrderNotFoundException(long orderId) {
        super("Заказ с id=" + orderId + " не найден");
    }
}
