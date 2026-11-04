package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;

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
}
