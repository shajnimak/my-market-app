package ru.yandex.practicum.mymarket;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Базовый класс интеграционных тестов. Конфигурация одинакова для всех наследников,
 * поэтому контекст приложения поднимается один раз и берётся из кеша.
 * Перед каждым тестом таблицы очищаются: реактивные транзакции не откатываются тестовым фреймворком.
 */
@SpringBootTest(properties = "market.demo-data.enabled=false")
@AutoConfigureWebTestClient
public abstract class AbstractIntegrationTest {

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected DatabaseClient databaseClient;

    @BeforeEach
    void cleanDatabase() {
        TestDatabase.clean(databaseClient).block();
    }
}
