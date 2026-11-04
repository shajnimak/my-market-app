package ru.yandex.practicum.payment.exception;

public class InsufficientFundsException extends RuntimeException {

    private final long balance;
    private final long amount;

    public InsufficientFundsException(long balance, long amount) {
        super("Недостаточно средств: баланс " + balance + " руб., требуется " + amount + " руб.");
        this.balance = balance;
        this.amount = amount;
    }

    public long getBalance() {
        return balance;
    }

    public long getAmount() {
        return amount;
    }
}
