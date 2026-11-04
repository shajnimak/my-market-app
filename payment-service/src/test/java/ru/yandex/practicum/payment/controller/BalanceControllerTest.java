package ru.yandex.practicum.payment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;

class BalanceControllerTest extends AbstractControllerTest {

    @Test
    void getBalance_shouldReturnBalanceAsJson() {
        when(accountService.getBalance()).thenReturn(Mono.just(42_000L));

        webTestClient.get().uri("/api/balance")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("{\"amount\": 42000}", true);
    }
}
