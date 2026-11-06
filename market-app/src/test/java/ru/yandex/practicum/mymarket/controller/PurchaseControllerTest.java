package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.exception.InsufficientFundsException;
import ru.yandex.practicum.mymarket.exception.PaymentAuthorizationException;
import ru.yandex.practicum.mymarket.exception.PaymentServiceUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static ru.yandex.practicum.mymarket.TestData.ALICE;
import static ru.yandex.practicum.mymarket.TestData.BOB;

class PurchaseControllerTest extends AbstractControllerTest {

    @Test
    void buy_shouldRedirectToNewOrder() {
        when(purchaseService.buy(ALICE.getId(), "alice")).thenReturn(Mono.just(7L));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().location("/orders/7?newOrder=true");
    }

    @Test
    void buy_shouldReturnBadRequest_whenCartEmpty() {
        when(purchaseService.buy(ALICE.getId(), "alice")).thenReturn(Mono.error(new EmptyCartException()));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void buy_shouldShowPaymentError_whenFundsInsufficient() {
        when(purchaseService.buy(ALICE.getId(), "alice")).thenReturn(Mono.error(new InsufficientFundsException("Недостаточно средств: баланс 100 руб.")));

        String html = webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(html).contains("Недостаточно средств: баланс 100 руб.");
    }

    @Test
    void buy_shouldShowServiceUnavailable_whenPaymentServiceDown() {
        when(purchaseService.buy(ALICE.getId(), "alice"))
                .thenReturn(Mono.error(new PaymentServiceUnavailableException(new RuntimeException("down"))));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void buy_shouldPayFromAccountOfAuthenticatedUser() {
        when(purchaseService.buy(BOB.getId(), "bob")).thenReturn(Mono.just(8L));

        clientOf(BOB).post().uri("/buy")
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().location("/orders/8?newOrder=true");
    }

    @Test
    void buy_shouldReturnUnauthorized_forAnonymous() {
        anonymousClient.mutateWith(csrf()).post().uri("/buy")
                .exchange()
                .expectStatus().isUnauthorized();

        verify(purchaseService, never()).buy(anyLong(), anyString());
    }

    @Test
    void buy_shouldShowServiceUnavailable_whenMarketNotAuthorizedInPaymentService() {
        when(purchaseService.buy(ALICE.getId(), "alice"))
                .thenReturn(Mono.error(new PaymentAuthorizationException(new RuntimeException("invalid_client"))));

        String html = webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(html).contains("Витрина не авторизована в сервисе платежей");
    }
}
