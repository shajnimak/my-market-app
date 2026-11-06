package ru.yandex.practicum.mymarket.dto;

/**
 * Можно ли оформить заказ из корзины: зависит от баланса в сервисе платежей.
 *
 * @param status  результат проверки
 * @param balance баланс счёта или {@code null}, если сервис платежей не запрашивался или недоступен
 */
public record Checkout(Status status, Long balance) {

    public enum Status {
        /** Средств хватает, кнопка оформления заказа доступна. */
        AVAILABLE,
        /** Средств на счёте меньше суммы корзины. */
        INSUFFICIENT_FUNDS,
        /** Сервис платежей не ответил. */
        PAYMENT_SERVICE_UNAVAILABLE,
        /** Корзина пуста, оформлять нечего. */
        EMPTY_CART
    }

    public static Checkout emptyCart() {
        return new Checkout(Status.EMPTY_CART, null);
    }

    public static Checkout unavailable() {
        return new Checkout(Status.PAYMENT_SERVICE_UNAVAILABLE, null);
    }

    public static Checkout forBalance(long balance, long total) {
        return new Checkout(balance >= total ? Status.AVAILABLE : Status.INSUFFICIENT_FUNDS, balance);
    }

    public boolean available() {
        return status == Status.AVAILABLE;
    }
}
