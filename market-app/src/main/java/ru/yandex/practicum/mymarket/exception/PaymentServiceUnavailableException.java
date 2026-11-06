package ru.yandex.practicum.mymarket.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Сервис платежей не ответил или ответил ошибкой, не предусмотренной спецификацией.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class PaymentServiceUnavailableException extends RuntimeException {

    public PaymentServiceUnavailableException(Throwable cause) {
        this("Сервис платежей недоступен, попробуйте оформить заказ позже", cause);
    }

    protected PaymentServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
