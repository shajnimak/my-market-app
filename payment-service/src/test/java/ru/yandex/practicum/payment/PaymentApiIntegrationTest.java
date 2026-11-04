package ru.yandex.practicum.payment;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.yandex.practicum.payment.api.model.Balance;
import ru.yandex.practicum.payment.api.model.ErrorResponse;
import ru.yandex.practicum.payment.api.model.PaymentRequest;
import ru.yandex.practicum.payment.api.model.PaymentResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет сервис целиком по HTTP: JSON-контракт из спецификации, списание и отказ при нехватке средств.
 */
class PaymentApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void payment_shouldDecreaseBalance() {
        long before = getBalance();

        PaymentResponse response = webTestClient.post().uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PaymentRequest(1_200L))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isEqualTo(new PaymentResponse(1_200L, before - 1_200));
        assertThat(getBalance()).isEqualTo(before - 1_200);
    }

    @Test
    void payment_shouldBeDeclinedAndKeepBalance_whenAmountExceedsBalance() {
        long before = getBalance();

        ErrorResponse error = webTestClient.post().uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PaymentRequest(before + 1))
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorResponse.CodeEnum.INSUFFICIENT_FUNDS);
        assertThat(getBalance()).isEqualTo(before);
    }

    @Test
    void unknownEndpoint_shouldReturnNotFound() {
        webTestClient.get().uri("/api/accounts")
                .exchange()
                .expectStatus().isNotFound();
    }

    private long getBalance() {
        Balance balance = webTestClient.get().uri("/api/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Balance.class)
                .returnResult()
                .getResponseBody();
        assertThat(balance).isNotNull();
        return balance.getAmount();
    }
}
