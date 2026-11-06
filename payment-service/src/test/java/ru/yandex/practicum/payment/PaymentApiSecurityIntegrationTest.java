package ru.yandex.practicum.payment;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.payment.api.model.PaymentRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Авторизация запросов к сервису платежей настоящими JWT: подпись ключом сервера авторизации, издатель,
 * получатель, срок действия и скоупы. Запрос без валидного токена не доходит до счетов и не меняет баланс.
 */
class PaymentApiSecurityIntegrationTest extends AbstractIntegrationTest {

    @Test
    void balance_shouldBeAvailable_withReadScope() {
        withToken(authorizationServer.token("payments.read")).get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.amount").isNumber();
    }

    @Test
    void request_shouldBeUnauthorized_withoutToken() {
        webTestClient.get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer");
    }

    @Test
    void request_shouldBeUnauthorized_withMalformedToken() {
        withToken("not-a-jwt").get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void request_shouldBeUnauthorized_whenTokenSignedByForeignKey() {
        withToken(authorizationServer.tokenSignedByForeignKey("payments.read")).get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void request_shouldBeUnauthorized_whenTokenIssuedByAnotherIssuer() {
        String token = authorizationServer.token(claims -> claims
                .issuer("http://evil.example")
                .claim("scope", List.of("payments.read")));

        withToken(token).get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void request_shouldBeUnauthorized_whenTokenIssuedForAnotherResourceServer() {
        String token = authorizationServer.token(claims -> claims
                .audience(List.of(TestAuthorizationServer.CLIENT_ID, "orders-service"))
                .claim("scope", List.of("payments.read")));

        withToken(token).get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void request_shouldBeUnauthorized_whenTokenExpired() {
        Instant past = Instant.now().minusSeconds(3600);
        String token = authorizationServer.token(claims -> claims
                .issuedAt(past.minusSeconds(300))
                .notBefore(past.minusSeconds(300))
                .expiresAt(past)
                .claim("scope", List.of("payments.read")));

        withToken(token).get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void balance_shouldBeForbidden_withoutReadScope() {
        withToken(authorizationServer.token("payments.write")).get().uri("/api/accounts/alice/balance")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void payment_shouldBeForbiddenAndKeepBalance_withoutWriteScope() {
        long before = balance("alice");

        pay(withToken(authorizationServer.token("payments.read")), "alice", 100)
                .expectStatus().isForbidden();

        assertThat(balance("alice")).isEqualTo(before);
    }

    @Test
    void payment_shouldBeUnauthorizedAndKeepBalance_withoutToken() {
        long before = balance("alice");

        pay(webTestClient, "alice", 100)
                .expectStatus().isUnauthorized();

        assertThat(balance("alice")).isEqualTo(before);
    }

    private static WebTestClient.ResponseSpec pay(WebTestClient client, String username, long amount) {
        return client.post().uri("/api/accounts/{username}/payments", username)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PaymentRequest(amount))
                .exchange();
    }

    private long balance(String username) {
        Long amount = authorizedClient().get().uri("/api/accounts/{username}/balance", username)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentBalance.class)
                .returnResult()
                .getResponseBody()
                .amount();
        assertThat(amount).isNotNull();
        return amount;
    }

    private record PaymentBalance(Long amount) {
    }
}
