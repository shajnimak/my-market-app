package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import ru.yandex.practicum.mymarket.TestDatabase;

/**
 * Общая конфигурация слоя доступа к данным: у всех наследников одинаковый слайс,
 * поэтому контекст {@code @DataR2dbcTest} создаётся один раз и берётся из кеша.
 */
@DataR2dbcTest
public abstract class AbstractRepositoryTest {

    @Autowired
    protected DatabaseClient databaseClient;

    @BeforeEach
    void cleanDatabase() {
        TestDatabase.clean(databaseClient).block();
    }
}
