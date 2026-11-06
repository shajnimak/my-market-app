package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.exception.InsufficientFundsException;
import ru.yandex.practicum.mymarket.exception.PaymentServiceUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PurchaseControllerTest extends AbstractControllerTest {

    @Test
    void buy_shouldRedirectToNewOrder() {
        when(purchaseService.buy()).thenReturn(Mono.just(7L));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().location("/orders/7?newOrder=true");
    }

    @Test
    void buy_shouldReturnBadRequest_whenCartEmpty() {
        when(purchaseService.buy()).thenReturn(Mono.error(new EmptyCartException()));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void buy_shouldShowPaymentError_whenFundsInsufficient() {
        when(purchaseService.buy()).thenReturn(Mono.error(new InsufficientFundsException("Недостаточно средств: баланс 100 руб.")));

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
        when(purchaseService.buy())
                .thenReturn(Mono.error(new PaymentServiceUnavailableException(new RuntimeException("down"))));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isEqualTo(503);
    }
}
