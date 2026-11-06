package ru.yandex.practicum.payment.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.payment.service.AccountService;

/**
 * Общая конфигурация веб-слоя: все контроллеры и один мок сервиса счёта,
 * поэтому контекст {@code @WebFluxTest} создаётся один раз и переиспользуется наследниками.
 */
@WebFluxTest
public abstract class AbstractControllerTest {

    @Autowired
    protected WebTestClient webTestClient;

    @MockitoBean
    protected AccountService accountService;
}
