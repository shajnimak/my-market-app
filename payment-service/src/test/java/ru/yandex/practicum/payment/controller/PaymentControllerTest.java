package ru.yandex.practicum.payment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.exception.InsufficientFundsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.payment.config.SecurityConfig.SCOPE_PAYMENTS_READ;
import static ru.yandex.practicum.payment.config.SecurityConfig.SCOPE_PAYMENTS_WRITE;

class PaymentControllerTest extends AbstractControllerTest {

    @Test
    void makePayment_shouldWithdrawAmountFromUserAccountAndReturnNewBalance() {
        when(accountService.withdraw("alice", 5_000)).thenReturn(Mono.just(95_000L));

        postPayment("{\"amount\": 5000}")
                .expectStatus().isOk()
                .expectBody().json("{\"amount\": 5000, \"balance\": 95000}", true);
    }

    @Test
    void makePayment_shouldReturnPaymentRequired_whenFundsInsufficient() {
        when(accountService.withdraw("alice", 5_000))
                .thenReturn(Mono.error(new InsufficientFundsException(3_000, 5_000)));

        postPayment("{\"amount\": 5000}")
                .expectStatus().isEqualTo(402)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INSUFFICIENT_FUNDS")
                .jsonPath("$.message").isEqualTo("Недостаточно средств: баланс 3000 руб., требуется 5000 руб.");
    }

    @Test
    void makePayment_shouldReturnBadRequest_whenAmountNotPositive() {
        postPayment("{\"amount\": 0}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.message").value(message -> assertThat((String) message)
                        .startsWith("amount:"));

        verify(accountService, never()).withdraw(anyString(), anyLong());
    }

    @Test
    void makePayment_shouldReturnBadRequest_whenAmountMissing() {
        postPayment("{}")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");

        verify(accountService, never()).withdraw(anyString(), anyLong());
    }

    @Test
    void makePayment_shouldReturnBadRequest_whenBodyMalformed() {
        postPayment("{\"amount\": ")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void makePayment_shouldReturnUnauthorized_withoutToken() {
        postPayment(webTestClient, "{\"amount\": 5000}")
                .expectStatus().isUnauthorized();

        verify(accountService, never()).withdraw(anyString(), anyLong());
    }

    @Test
    void makePayment_shouldReturnForbidden_whenTokenHasOnlyReadScope() {
        postPayment(withAuthorities(SCOPE_PAYMENTS_READ), "{\"amount\": 5000}")
                .expectStatus().isForbidden();

        verify(accountService, never()).withdraw(anyString(), anyLong());
    }

    private ResponseSpec postPayment(String json) {
        return postPayment(withAuthorities(SCOPE_PAYMENTS_WRITE), json);
    }

    private static ResponseSpec postPayment(WebTestClient client, String json) {
        return client.post().uri("/api/accounts/alice/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange();
    }
}
