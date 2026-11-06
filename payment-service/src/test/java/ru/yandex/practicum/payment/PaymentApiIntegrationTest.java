package ru.yandex.practicum.payment;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.yandex.practicum.payment.api.model.Balance;
import ru.yandex.practicum.payment.api.model.ErrorResponse;
import ru.yandex.practicum.payment.api.model.PaymentRequest;
import ru.yandex.practicum.payment.api.model.PaymentResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет сервис целиком по HTTP с авторизованным клиентом: JSON-контракт из спецификации,
 * списание со счёта конкретного покупателя и отказ при нехватке средств.
 */
class PaymentApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void payment_shouldDecreaseBalanceOfThatUserOnly() {
        long aliceBefore = getBalance("alice");
        long bobBefore = getBalance("bob");

        PaymentResponse response = authorizedClient().post().uri("/api/accounts/{username}/payments", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PaymentRequest(1_200L))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isEqualTo(new PaymentResponse(1_200L, aliceBefore - 1_200));
        assertThat(getBalance("alice")).isEqualTo(aliceBefore - 1_200);
        assertThat(getBalance("bob")).isEqualTo(bobBefore);
    }

    @Test
    void newAccount_shouldStartWithConfiguredBalance() {
        assertThat(getBalance("newcomer")).isEqualTo(50_000);
        assertThat(getBalance("poor")).isEqualTo(100);
    }

    @Test
    void payment_shouldBeDeclinedAndKeepBalance_whenAmountExceedsBalance() {
        long before = getBalance("poor");

        ErrorResponse error = authorizedClient().post().uri("/api/accounts/{username}/payments", "poor")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PaymentRequest(before + 1))
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorResponse.CodeEnum.INSUFFICIENT_FUNDS);
        assertThat(getBalance("poor")).isEqualTo(before);
    }

    @Test
    void unknownEndpoint_shouldReturnNotFound_forAuthorizedClient() {
        authorizedClient().get().uri("/api/accounts")
                .exchange()
                .expectStatus().isNotFound();
    }

    private long getBalance(String username) {
        Balance balance = authorizedClient().get().uri("/api/accounts/{username}/balance", username)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Balance.class)
                .returnResult()
                .getResponseBody();
        assertThat(balance).isNotNull();
        return balance.getAmount();
    }
}
