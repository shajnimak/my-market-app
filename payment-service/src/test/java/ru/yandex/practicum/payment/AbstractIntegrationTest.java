package ru.yandex.practicum.payment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Базовый класс интеграционных тестов: приложение запускается на случайном порту во встроенном Netty,
 * запросы идут по HTTP через {@link WebTestClient} с настоящими подписанными JWT.
 * Сервер авторизации заменён {@link TestAuthorizationServer}: он один на все тесты, поэтому адрес JWK Set
 * одинаков и конфигурация контекста у всех наследников совпадает — контекст поднимается один раз и берётся из кеша.
 * Балансы счетов — общее состояние контекста, поэтому тесты проверяют их изменение, а не абсолютное значение.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "payment.account.initial-balance=50000",
                "payment.account.balances.poor=100"
        })
public abstract class AbstractIntegrationTest {

    protected static final TestAuthorizationServer authorizationServer = new TestAuthorizationServer();

    @DynamicPropertySource
    static void authorizationServerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", authorizationServer::jwkSetUri);
    }

    @Autowired
    protected WebTestClient webTestClient;

    /**
     * Клиент с токеном витрины, в котором есть оба скоупа сервиса платежей.
     */
    protected WebTestClient authorizedClient() {
        return withToken(authorizationServer.token("payments.read", "payments.write"));
    }

    protected WebTestClient withToken(String token) {
        return webTestClient.mutate()
                .defaultHeaders(headers -> headers.setBearerAuth(token))
                .build();
    }
}
