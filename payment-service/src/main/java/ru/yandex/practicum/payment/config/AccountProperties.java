package ru.yandex.practicum.payment.config;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Настройки счетов покупателей.
 *
 * @param initialBalance начальный баланс счёта, который открывается покупателю при первом обращении
 *                       ({@code payment.account.initial-balance})
 * @param balances       начальные балансы отдельных покупателей по логину ({@code payment.account.balances.<логин>})
 */
@Validated
@ConfigurationProperties(prefix = "payment.account")
public record AccountProperties(@PositiveOrZero long initialBalance,
                                Map<String, @PositiveOrZero Long> balances) {

    public AccountProperties {
        balances = balances == null ? Map.of() : Map.copyOf(balances);
    }

    public long initialBalanceOf(String username) {
        return balances.getOrDefault(username, initialBalance);
    }
}
