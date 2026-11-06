package ru.yandex.practicum.mymarket.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("Нельзя оформить заказ: корзина пуста");
    }
}
