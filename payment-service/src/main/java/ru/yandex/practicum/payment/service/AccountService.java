package ru.yandex.practicum.payment.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.config.AccountProperties;
import ru.yandex.practicum.payment.exception.InsufficientFundsException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Счета покупателей, по одному на логин покупателя в витрине. Счёт открывается при первом обращении
 * с начальным балансом из конфигурации и хранится в памяти.
 * Списание атомарно: проверка остатка и вычитание выполняются одной операцией compare-and-set,
 * поэтому параллельные платежи не уводят баланс в минус.
 */
@Service
public class AccountService {

    private final AccountProperties properties;
    private final ConcurrentMap<String, AtomicLong> accounts = new ConcurrentHashMap<>();

    public AccountService(AccountProperties properties) {
        this.properties = properties;
    }

    public Mono<Long> getBalance(String username) {
        return Mono.fromSupplier(() -> account(username).get());
    }

    /**
     * Списывает сумму со счёта покупателя.
     *
     * @param username логин покупателя
     * @param amount   положительная сумма к списанию
     * @return баланс после списания или {@link InsufficientFundsException}, если средств не хватает
     */
    public Mono<Long> withdraw(String username, long amount) {
        if (amount <= 0) {
            return Mono.error(new IllegalArgumentException("Сумма платежа должна быть положительной"));
        }
        return Mono.fromCallable(() -> {
            AtomicLong balance = account(username);
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

    private AtomicLong account(String username) {
        return accounts.computeIfAbsent(username, name -> new AtomicLong(properties.initialBalanceOf(name)));
    }
}
