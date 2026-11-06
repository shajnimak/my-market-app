package ru.yandex.practicum.mymarket.exception;

/**
 * Витрина не смогла авторизоваться для запросов в сервис платежей: сервер авторизации не выдал токен
 * (неверные client_id/client_secret, сервер авторизации недоступен) или сервис платежей не принял токен (401/403).
 * Для покупателя это та же недоступность оплаты, поэтому исключение — частный случай
 * {@link PaymentServiceUnavailableException}.
 */
public class PaymentAuthorizationException extends PaymentServiceUnavailableException {

    public PaymentAuthorizationException(Throwable cause) {
        super("Витрина не авторизована в сервисе платежей, оформить заказ сейчас нельзя", cause);
    }
}
