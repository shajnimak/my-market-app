package ru.yandex.practicum.payment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Базовый класс интеграционных тестов: приложение запускается на случайном порту во встроенном Netty,
 * запросы идут по HTTP через {@link WebTestClient}. Конфигурация у всех наследников одинакова,
 * поэтому контекст поднимается один раз и берётся из кеша.
 * Баланс счёта — общее состояние контекста, поэтому тесты проверяют его изменение, а не абсолютное значение.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "payment.account.initial-balance=50000")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected WebTestClient webTestClient;
}
