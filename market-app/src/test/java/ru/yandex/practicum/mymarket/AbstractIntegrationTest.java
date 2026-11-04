package ru.yandex.practicum.mymarket;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Базовый класс интеграционных тестов. Конфигурация одинакова для всех наследников,
 * поэтому контекст приложения (вместе с контейнером Redis) поднимается один раз и берётся из кеша.
 * Сервис платежей заменён HTTP-заглушкой {@link PaymentServiceStub}: она одна на все тесты,
 * так что адрес в {@code market.payment.url} одинаков и не мешает кешированию контекста.
 * Перед каждым тестом очищаются таблицы и кеш (реактивные транзакции не откатываются тестовым фреймворком),
 * а заглушка возвращается в исходное состояние.
 */
@SpringBootTest(properties = "market.demo-data.enabled=false")
@AutoConfigureWebTestClient
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final PaymentServiceStub paymentServiceStub = new PaymentServiceStub();

    @DynamicPropertySource
    static void paymentServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("market.payment.url", paymentServiceStub::url);
    }

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected DatabaseClient databaseClient;

    @Autowired
    protected ReactiveStringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanStorage() {
        TestDatabase.clean(databaseClient).block();
        redisTemplate.execute(connection -> connection.serverCommands().flushDb()).blockLast();
        paymentServiceStub.reset();
    }
}
