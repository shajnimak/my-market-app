package ru.yandex.practicum.mymarket.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Сервис платежей отклонил списание: на счёте не хватает средств.
 */
@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
