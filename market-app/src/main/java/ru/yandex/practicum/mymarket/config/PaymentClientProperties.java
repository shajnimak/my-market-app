package ru.yandex.practicum.mymarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Подключение к сервису платежей. Таймауты короткие: если сервис недоступен,
 * страница корзины должна быстро показать сообщение, а не ждать ответа.
 *
 * @param url                  адрес сервиса платежей
 * @param connectTimeout       таймаут установки соединения
 * @param responseTimeout      таймаут ожидания ответа
 * @param clientRegistrationId регистрация OAuth2-клиента ({@code spring.security.oauth2.client.registration.<id>}),
 *                             по которой витрина получает токен для запросов в сервис платежей
 */
@ConfigurationProperties(prefix = "market.payment")
public record PaymentClientProperties(String url,
                                      Duration connectTimeout,
                                      Duration responseTimeout,
                                      String clientRegistrationId) {
}
