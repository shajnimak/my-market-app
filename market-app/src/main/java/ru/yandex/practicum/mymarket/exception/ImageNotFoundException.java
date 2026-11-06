package ru.yandex.practicum.mymarket.exception;

public class ImageNotFoundException extends NotFoundException {

    public ImageNotFoundException(long itemId) {
        super("Изображение товара с id=" + itemId + " не найдено");
    }
}
