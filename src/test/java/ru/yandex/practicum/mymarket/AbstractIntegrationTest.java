package ru.yandex.practicum.mymarket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Базовый класс интеграционных тестов. Конфигурация одинакова для всех наследников,
 * поэтому контекст приложения поднимается один раз и берётся из кеша.
 * Каждый тест выполняется в транзакции, которая откатывается после теста.
 */
@SpringBootTest(properties = "market.demo-data.enabled=false")
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
}
