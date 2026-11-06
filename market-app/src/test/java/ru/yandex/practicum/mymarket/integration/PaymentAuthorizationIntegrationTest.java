package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.service.ItemService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Авторизация витрины в сервисе платежей по OAuth2 Client Credentials Flow в работающем приложении:
 * токен запрашивается у сервера авторизации с client_id/client_secret витрины, передаётся в заголовке
 * {@code Authorization: Bearer}, переиспользуется и заменяется, когда сервис платежей перестаёт его принимать.
 * Если сервер авторизации не выдаёт токен, запросы в сервис платежей не отправляются и оплата недоступна.
 */
class PaymentAuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void putItemIntoCart() {
        long itemId = itemService.createItem(new NewItem("Ракетка", "Теннисная", 7800,
                new ImageData("image/png", new byte[]{1}))).block();
        webTestClient.post().uri("/items/{id}", itemId)
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().isSeeOther();
    }

    @Test
    void paymentRequests_shouldCarryTokenObtainedByClientCredentials() {
        assertThat(getCartHtml()).contains("На счёте: ");
        webTestClient.post().uri("/buy").exchange().expectStatus().isSeeOther();

        assertThat(authorizationServerStub.getTokenRequests()).singleElement().satisfies(form -> {
            assertThat(form).containsEntry("grant_type", "client_credentials");
            assertThat(form.get("scope").split(" ")).containsExactlyInAnyOrder("payments.read", "payments.write");
        });
        assertThat(paymentServiceStub.getUsedTokens()).hasSize(2).containsOnly("access-token-" + tokenNumber());
        assertThat(paymentServiceStub.getUnauthorizedRequests()).isZero();
    }

    @Test
    void token_shouldBeSharedByAllUsers_becauseItIdentifiesMarketAppNotBuyer() {
        getCartHtml();
        clientOf(bob).get().uri("/cart/items").exchange().expectStatus().isOk();

        assertThat(authorizationServerStub.getTokenRequests()).hasSize(1);
    }

    @Test
    void payment_shouldBeUnavailable_whenAuthorizationServerRejectsMarketApp() {
        authorizationServerStub.setClientRegistered(false);

        assertThat(getCartHtml()).contains("disabled=\"disabled\"", "Сервис платежей недоступен");
        String html = webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(html).contains("Витрина не авторизована в сервисе платежей");
        assertThat(paymentServiceStub.getUsedTokens()).isEmpty();
        assertThat(paymentServiceStub.getUnauthorizedRequests()).isZero();
        assertThat(paymentServiceStub.getPayments()).isEmpty();
        StepVerifier.create(orderRepository.count()).expectNext(0L).verifyComplete();
    }

    @Test
    void rejectedToken_shouldBeReplacedWithNewOne() {
        assertThat(getCartHtml()).contains("На счёте: ");
        authorizationServerStub.revokeTokens();

        assertThat(getCartHtml()).contains("Сервис платежей недоступен");
        assertThat(getCartHtml()).contains("На счёте: ");

        assertThat(authorizationServerStub.getTokenRequests()).hasSize(2);
        assertThat(paymentServiceStub.getUnauthorizedRequests()).isEqualTo(1);
    }

    private String tokenNumber() {
        String token = paymentServiceStub.getUsedTokens().getFirst();
        return token.substring("access-token-".length());
    }

    private String getCartHtml() {
        return webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
