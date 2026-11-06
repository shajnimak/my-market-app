package ru.yandex.practicum.payment.config;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки счёта: начальный баланс задаётся в конфигурации ({@code payment.account.initial-balance}).
 */
@Validated
@ConfigurationProperties(prefix = "payment.account")
public record AccountProperties(@PositiveOrZero long initialBalance) {
}
