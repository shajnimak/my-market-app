package ru.yandex.practicum.mymarket.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PurchaseService;
import ru.yandex.practicum.mymarket.service.ShowcaseService;

/**
 * Общая конфигурация веб-слоя: все контроллеры и одинаковый набор моков сервисов,
 * благодаря чему контекст {@code @WebFluxTest} создаётся один раз и переиспользуется всеми наследниками.
 */
@WebFluxTest
public abstract class AbstractControllerTest {

    @Autowired
    protected WebTestClient webTestClient;

    @MockitoBean
    protected ItemService itemService;

    @MockitoBean
    protected ShowcaseService showcaseService;

    @MockitoBean
    protected CartService cartService;

    @MockitoBean
    protected OrderService orderService;

    @MockitoBean
    protected PurchaseService purchaseService;

    protected String getHtml(String uri) {
        return webTestClient.get().uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/html")
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    protected static int occurrences(String text, String fragment) {
        int count = 0;
        for (int from = text.indexOf(fragment); from >= 0; from = text.indexOf(fragment, from + fragment.length())) {
            count++;
        }
        return count;
    }
}
