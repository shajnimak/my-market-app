package ru.yandex.practicum.mymarket.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.config.PaymentClientConfig;
import ru.yandex.practicum.mymarket.config.PaymentClientProperties;
import ru.yandex.practicum.mymarket.exception.InsufficientFundsException;
import ru.yandex.practicum.mymarket.exception.PaymentServiceUnavailableException;
import ru.yandex.practicum.mymarket.payment.client.ApiClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет сгенерированный клиент вместе с {@link PaymentClientConfig} и переводом ошибок
 * на настоящем HTTP: сервис платежей заменён заглушкой {@link MockWebServer}.
 */
class PaymentServiceTest {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(300);

    private MockWebServer server;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        paymentService = createPaymentService(server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void getBalance_shouldRequestBalanceEndpoint() throws InterruptedException {
        server.enqueue(json(200, "{\"amount\": 12000}"));

        StepVerifier.create(paymentService.getBalance())
                .expectNext(12_000L)
                .verifyComplete();

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/api/balance");
    }

    @Test
    void pay_shouldPostAmountAsJsonAndReturnNewBalance() throws InterruptedException {
        server.enqueue(json(200, "{\"amount\": 3000, \"balance\": 9000}"));

        StepVerifier.create(paymentService.pay(3_000))
                .expectNext(9_000L)
                .verifyComplete();

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/payments");
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        assertThat(request.getBody().readUtf8()).isEqualToIgnoringWhitespace("{\"amount\":3000}");
    }

    @Test
    void pay_shouldFailWithInsufficientFunds_whenPaymentDeclined() {
        server.enqueue(json(402, "{\"code\": \"INSUFFICIENT_FUNDS\", \"message\": \"Недостаточно средств: баланс 100 руб.\"}"));

        StepVerifier.create(paymentService.pay(3_000))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InsufficientFundsException.class)
                        .hasMessage("Недостаточно средств: баланс 100 руб."))
                .verify();
    }

    @Test
    void pay_shouldFailWithUnavailable_whenServiceReturnsServerError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(paymentService.pay(3_000))
                .expectError(PaymentServiceUnavailableException.class)
                .verify();
    }

    @Test
    void getBalance_shouldFailWithUnavailable_whenServiceDoesNotAnswerInTime() {
        server.enqueue(json(200, "{\"amount\": 12000}").setHeadersDelay(2, TimeUnit.SECONDS));

        StepVerifier.create(paymentService.getBalance())
                .expectError(PaymentServiceUnavailableException.class)
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void getBalance_shouldFailWithUnavailable_whenServiceIsDown() throws IOException {
        String url = server.url("/").toString();
        server.shutdown();
        PaymentService unreachable = createPaymentService(url);

        StepVerifier.create(unreachable.getBalance())
                .expectError(PaymentServiceUnavailableException.class)
                .verify(Duration.ofSeconds(2));
    }

    private static PaymentService createPaymentService(String url) {
        PaymentClientConfig config = new PaymentClientConfig();
        PaymentClientProperties properties = new PaymentClientProperties(url, RESPONSE_TIMEOUT, RESPONSE_TIMEOUT);
        ApiClient apiClient = config.paymentApiClient(WebClient.builder(), properties);
        return new PaymentService(config.balanceApi(apiClient), config.paymentsApi(apiClient));
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
