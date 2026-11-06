package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.mymarket.security.MarketUserDetails;
import ru.yandex.practicum.mymarket.security.MarketUserDetailsService;
import ru.yandex.practicum.mymarket.security.SecurityConfig;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PurchaseService;
import ru.yandex.practicum.mymarket.service.ShowcaseService;
import ru.yandex.practicum.mymarket.service.UserService;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;
import static ru.yandex.practicum.mymarket.TestData.ALICE;

/**
 * Общая конфигурация веб-слоя: все контроллеры, правила доступа {@link SecurityConfig} и одинаковый набор моков
 * сервисов, благодаря чему контекст {@code @WebFluxTest} создаётся один раз и переиспользуется всеми наследниками.
 * <p>
 * {@link #webTestClient} работает от имени вошедшего покупателя {@code alice} с CSRF-токеном,
 * {@link #anonymousClient} — от имени анонимного посетителя.
 */
@WebFluxTest
@Import(SecurityConfig.class)
public abstract class AbstractControllerTest {

    @Autowired
    protected WebTestClient anonymousClient;

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

    @MockitoBean
    protected UserService userService;

    @MockitoBean
    protected MarketUserDetailsService userDetailsService;

    @BeforeEach
    void authenticate() {
        webTestClient = clientOf(ALICE);
    }

    protected WebTestClient clientOf(MarketUserDetails user) {
        return anonymousClient
                .mutateWith(mockAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities())))
                .mutateWith(csrf());
    }

    protected String getHtml(String uri) {
        return getHtml(webTestClient, uri);
    }

    protected static String getHtml(WebTestClient client, String uri) {
        return client.get().uri(uri)
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
