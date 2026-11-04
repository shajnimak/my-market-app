package ru.yandex.practicum.mymarket.exception;

public class ItemNotFoundException extends NotFoundException {

    public ItemNotFoundException(long itemId) {
        super("Товар с id=" + itemId + " не найден");
    }
}
