package ru.yandex.practicum.mymarket.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки кеша товаров в Redis.
 *
 * @param ttl время жизни записей о товарах в кеше
 */
@ConfigurationProperties(prefix = "market.cache")
public record ItemCacheProperties(Duration ttl) {
}
