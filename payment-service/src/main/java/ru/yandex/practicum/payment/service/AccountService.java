package ru.yandex.practicum.payment.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.config.AccountProperties;
import ru.yandex.practicum.payment.exception.InsufficientFundsException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Счёт покупателя. Баланс берётся из конфигурации при старте и хранится в памяти.
 * Списание атомарно: проверка остатка и вычитание выполняются одной операцией compare-and-set,
 * поэтому параллельные платежи не уводят баланс в минус.
 */
@Service
public class AccountService {

    private final AtomicLong balance;

    public AccountService(AccountProperties properties) {
        this.balance = new AtomicLong(properties.initialBalance());
    }

    public Mono<Long> getBalance() {
        return Mono.fromSupplier(balance::get);
    }

    /**
     * Списывает сумму со счёта.
     *
     * @param amount положительная сумма к списанию
     * @return баланс после списания или {@link InsufficientFundsException}, если средств не хватает
     */
    public Mono<Long> withdraw(long amount) {
        if (amount <= 0) {
            return Mono.error(new IllegalArgumentException("Сумма платежа должна быть положительной"));
        }
        return Mono.fromCallable(() -> {
            long current;
            do {
                current = balance.get();
                if (current < amount) {
                    throw new InsufficientFundsException(current, amount);
                }
            } while (!balance.compareAndSet(current, current - amount));
            return current - amount;
        });
    }
}
