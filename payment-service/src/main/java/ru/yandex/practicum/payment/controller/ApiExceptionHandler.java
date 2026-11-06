package ru.yandex.practicum.payment.controller;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import ru.yandex.practicum.payment.api.model.ErrorResponse;
import ru.yandex.practicum.payment.api.model.ErrorResponse.CodeEnum;
import ru.yandex.practicum.payment.exception.InsufficientFundsException;

import java.util.stream.Collectors;

/**
 * Переводит ошибки в ответы {@link ErrorResponse} из спецификации.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        return error(HttpStatus.PAYMENT_REQUIRED, CodeEnum.INSUFFICIENT_FUNDS, e.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(WebExchangeBindException e) {
        String message = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, CodeEnum.INVALID_REQUEST, message);
    }

    /**
     * Нарушение ограничений параметров пути (например, недопустимый логин): интерфейсы контроллеров
     * помечены {@code @Validated}, поэтому параметры проверяет AOP-прокси.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, CodeEnum.INVALID_REQUEST, message);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest(ServerWebInputException e) {
        return error(HttpStatus.BAD_REQUEST, CodeEnum.INVALID_REQUEST, "Некорректное тело запроса");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, CodeEnum code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
