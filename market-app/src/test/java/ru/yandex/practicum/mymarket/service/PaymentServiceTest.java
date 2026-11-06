package ru.yandex.practicum.mymarket.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.AuthorizationServerStub;
import ru.yandex.practicum.mymarket.config.ClientCredentialsExchangeFilter;
import ru.yandex.practicum.mymarket.config.PaymentClientConfig;
import ru.yandex.practicum.mymarket.config.PaymentClientProperties;
import ru.yandex.practicum.mymarket.exception.InsufficientFundsException;
import ru.yandex.practicum.mymarket.exception.PaymentAuthorizationException;
import ru.yandex.practicum.mymarket.exception.PaymentServiceUnavailableException;
import ru.yandex.practicum.mymarket.payment.client.ApiClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет сгенерированный клиент вместе с {@link PaymentClientConfig}, получением токена по Client Credentials Flow
 * и переводом ошибок на настоящем HTTP: сервис платежей заменён {@link MockWebServer},
 * token endpoint сервера авторизации — {@link AuthorizationServerStub}.
 */
class PaymentServiceTest {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(300);
    private static final String REGISTRATION_ID = "payment-service";

    private final AuthorizationServerStub authorizationServer = new AuthorizationServerStub();

    private MockWebServer server;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        paymentService = createPaymentService(server.url("/").toString(), AuthorizationServerStub.CLIENT_SECRET);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void getBalance_shouldRequestAccountOfUserWithBearerToken() throws InterruptedException {
        server.enqueue(json(200, "{\"amount\": 12000}"));

        StepVerifier.create(paymentService.getBalance("alice"))
                .expectNext(12_000L)
                .verifyComplete();

        RecordedRequest request = takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/api/accounts/alice/balance");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer access-token-1");
    }

    @Test
    void firstRequest_shouldObtainTokenWithClientCredentialsAndPaymentScopes() {
        server.enqueue(json(200, "{\"amount\": 12000}"));

        StepVerifier.create(paymentService.getBalance("alice")).expectNextCount(1).verifyComplete();

        assertThat(authorizationServer.getTokenRequests()).singleElement().satisfies(form -> {
            assertThat(form).containsEntry("grant_type", "client_credentials");
            assertThat(form.get("scope").split(" ")).containsExactlyInAnyOrder("payments.read", "payments.write");
        });
    }

    @Test
    void token_shouldBeReusedForFollowingRequestsOfAnyUser() throws InterruptedException {
        server.enqueue(json(200, "{\"amount\": 12000}"));
        server.enqueue(json(200, "{\"amount\": 3000, \"balance\": 9000}"));

        StepVerifier.create(paymentService.getBalance("alice")
                        .then(paymentService.pay("bob", 3_000)))
                .expectNext(9_000L)
                .verifyComplete();

        assertThat(authorizationServer.getTokenRequests()).hasSize(1);
        assertThat(takeRequest().getHeader("Authorization")).isEqualTo("Bearer access-token-1");
        assertThat(takeRequest().getHeader("Authorization")).isEqualTo("Bearer access-token-1");
    }

    @Test
    void pay_shouldPostAmountAsJsonToAccountOfUserAndReturnNewBalance() throws InterruptedException {
        server.enqueue(json(200, "{\"amount\": 3000, \"balance\": 9000}"));

        StepVerifier.create(paymentService.pay("alice", 3_000))
                .expectNext(9_000L)
                .verifyComplete();

        RecordedRequest request = takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/accounts/alice/payments");
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        assertThat(request.getHeader("Authorization")).startsWith("Bearer ");
        assertThat(request.getBody().readUtf8()).isEqualToIgnoringWhitespace("{\"amount\":3000}");
    }

    @Test
    void pay_shouldFailWithInsufficientFunds_whenPaymentDeclined() {
        server.enqueue(json(402, "{\"code\": \"INSUFFICIENT_FUNDS\", \"message\": \"Недостаточно средств: баланс 100 руб.\"}"));

        StepVerifier.create(paymentService.pay("alice", 3_000))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InsufficientFundsException.class)
                        .hasMessage("Недостаточно средств: баланс 100 руб."))
                .verify();
    }

    @Test
    void pay_shouldFailWithUnavailable_whenServiceReturnsServerError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(paymentService.pay("alice", 3_000))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isExactlyInstanceOf(PaymentServiceUnavailableException.class))
                .verify();
    }

    @Test
    void request_shouldNotReachPaymentService_whenAuthorizationServerRejectsClient() {
        PaymentService unauthorized = createPaymentService(server.url("/").toString(), "wrong-secret");

        StepVerifier.create(unauthorized.getBalance("alice"))
                .expectError(PaymentAuthorizationException.class)
                .verify(Duration.ofSeconds(2));

        assertThat(authorizationServer.getTokenRequests()).hasSize(1);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void rejectedToken_shouldFailRequestAndBeReplacedByNewTokenOnNextRequest() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Bearer"));
        server.enqueue(json(200, "{\"amount\": 12000}"));

        StepVerifier.create(paymentService.getBalance("alice"))
                .expectError(PaymentAuthorizationException.class)
                .verify();
        StepVerifier.create(paymentService.getBalance("alice"))
                .expectNext(12_000L)
                .verifyComplete();

        assertThat(authorizationServer.getTokenRequests()).hasSize(2);
        assertThat(takeRequest().getHeader("Authorization")).isEqualTo("Bearer access-token-1");
        assertThat(takeRequest().getHeader("Authorization")).isEqualTo("Bearer access-token-2");
    }

    @Test
    void getBalance_shouldFailWithUnavailable_whenServiceDoesNotAnswerInTime() {
        server.enqueue(json(200, "{\"amount\": 12000}").setHeadersDelay(2, TimeUnit.SECONDS));

        StepVerifier.create(paymentService.getBalance("alice"))
                .expectError(PaymentServiceUnavailableException.class)
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void getBalance_shouldFailWithUnavailable_whenServiceIsDown() throws IOException {
        String url = server.url("/").toString();
        server.shutdown();
        PaymentService unreachable = createPaymentService(url, AuthorizationServerStub.CLIENT_SECRET);

        StepVerifier.create(unreachable.getBalance("alice"))
                .expectError(PaymentServiceUnavailableException.class)
                .verify(Duration.ofSeconds(2));
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        return request;
    }

    private PaymentService createPaymentService(String url, String clientSecret) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(AuthorizationServerStub.CLIENT_ID)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("payments.read", "payments.write")
                .tokenUri(authorizationServer.tokenUri())
                .build();
        ReactiveClientRegistrationRepository registrations = new InMemoryReactiveClientRegistrationRepository(registration);
        ReactiveOAuth2AuthorizedClientService authorizedClients = new InMemoryReactiveOAuth2AuthorizedClientService(registrations);

        PaymentClientConfig config = new PaymentClientConfig();
        PaymentClientProperties properties =
                new PaymentClientProperties(url, RESPONSE_TIMEOUT, RESPONSE_TIMEOUT, REGISTRATION_ID);
        ReactiveOAuth2AuthorizedClientManager manager =
                config.paymentAuthorizedClientManager(registrations, authorizedClients);
        ClientCredentialsExchangeFilter filter =
                config.paymentClientCredentialsFilter(manager, authorizedClients, properties);
        ApiClient apiClient = config.paymentApiClient(WebClient.builder(), properties, filter);
        return new PaymentService(config.balanceApi(apiClient), config.paymentsApi(apiClient));
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
