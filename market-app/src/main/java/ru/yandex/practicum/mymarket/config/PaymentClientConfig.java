package ru.yandex.practicum.mymarket.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import ru.yandex.practicum.mymarket.payment.client.ApiClient;
import ru.yandex.practicum.mymarket.payment.client.api.BalanceApi;
import ru.yandex.practicum.mymarket.payment.client.api.PaymentsApi;

/**
 * HTTP-клиент сервиса платежей. Классы {@link ApiClient}, {@link BalanceApi} и {@link PaymentsApi}
 * сгенерированы по спецификации {@code openapi/payment-api.yaml}; здесь они получают {@link WebClient},
 * собранный из автоконфигурированного {@link WebClient.Builder} (кодеки Jackson Spring Boot) с таймаутами Reactor Netty
 * и фильтром {@link ClientCredentialsExchangeFilter}, который добавляет к каждому запросу access-токен
 * сервера авторизации.
 * <p>
 * Регистрацию клиента ({@code client_id}, {@code client_secret}, скоупы, адрес token endpoint) Spring Boot
 * читает из свойств {@code spring.security.oauth2.client.*} и создаёт {@link ReactiveClientRegistrationRepository}
 * и {@link ReactiveOAuth2AuthorizedClientService}.
 */
@Configuration
@EnableConfigurationProperties(PaymentClientProperties.class)
public class PaymentClientConfig {

    /**
     * Менеджер токенов для вызовов вне контекста пользователя: работает с {@link ReactiveOAuth2AuthorizedClientService},
     * а не с веб-сессией, и умеет только Client Credentials Flow.
     */
    @Bean
    public ReactiveOAuth2AuthorizedClientManager paymentAuthorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ReactiveOAuth2AuthorizedClientService authorizedClientService) {
        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    @Bean
    public ClientCredentialsExchangeFilter paymentClientCredentialsFilter(
            ReactiveOAuth2AuthorizedClientManager paymentAuthorizedClientManager,
            ReactiveOAuth2AuthorizedClientService authorizedClientService,
            PaymentClientProperties properties) {
        return new ClientCredentialsExchangeFilter(paymentAuthorizedClientManager, authorizedClientService,
                properties.clientRegistrationId(), properties.clientRegistrationId());
    }

    @Bean
    public ApiClient paymentApiClient(WebClient.Builder webClientBuilder,
                                      PaymentClientProperties properties,
                                      ClientCredentialsExchangeFilter paymentClientCredentialsFilter) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
                .responseTimeout(properties.responseTimeout());
        WebClient webClient = webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(paymentClientCredentialsFilter)
                .build();
        return new ApiClient(webClient)
                .setBasePath(properties.url());
    }

    @Bean
    public BalanceApi balanceApi(ApiClient paymentApiClient) {
        return new BalanceApi(paymentApiClient);
    }

    @Bean
    public PaymentsApi paymentsApi(ApiClient paymentApiClient) {
        return new PaymentsApi(paymentApiClient);
    }
}
