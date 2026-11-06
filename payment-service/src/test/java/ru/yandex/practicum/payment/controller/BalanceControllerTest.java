package ru.yandex.practicum.payment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.payment.config.SecurityConfig.SCOPE_PAYMENTS_READ;
import static ru.yandex.practicum.payment.config.SecurityConfig.SCOPE_PAYMENTS_WRITE;

class BalanceControllerTest extends AbstractControllerTest {

    @Test
    void getBalance_shouldReturnBalanceOfRequestedUserAsJson() {
        when(accountService.getBalance("alice")).thenReturn(Mono.just(42_000L));

        withAuthorities(SCOPE_PAYMENTS_READ).get().uri("/api/accounts/alice/balance")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("{\"amount\": 42000}", true);
    }

    @Test
    void getBalance_shouldReturnUnauthorized_withoutToken() {
        webTestClient.get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer");

        verify(accountService, never()).getBalance(anyString());
    }

    @Test
    void getBalance_shouldReturnForbidden_whenTokenHasNoReadScope() {
        withAuthorities(SCOPE_PAYMENTS_WRITE).get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isForbidden();

        verify(accountService, never()).getBalance(anyString());
    }

    @Test
    void getBalance_shouldReturnBadRequest_whenUsernameInvalid() {
        withAuthorities(SCOPE_PAYMENTS_READ).get().uri("/api/accounts/a!/balance")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");

        verify(accountService, never()).getBalance(anyString());
    }
}
