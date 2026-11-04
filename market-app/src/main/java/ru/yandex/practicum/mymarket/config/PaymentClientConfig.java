package ru.yandex.practicum.mymarket.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import ru.yandex.practicum.mymarket.payment.client.ApiClient;
import ru.yandex.practicum.mymarket.payment.client.api.BalanceApi;
import ru.yandex.practicum.mymarket.payment.client.api.PaymentsApi;

/**
 * HTTP-клиент сервиса платежей. Классы {@link ApiClient}, {@link BalanceApi} и {@link PaymentsApi}
 * сгенерированы по спецификации {@code openapi/payment-api.yaml}; здесь они получают {@link WebClient},
 * собранный из автоконфигурированного {@link WebClient.Builder} (кодеки Jackson Spring Boot) с таймаутами Reactor Netty.
 */
@Configuration
@EnableConfigurationProperties(PaymentClientProperties.class)
public class PaymentClientConfig {

    @Bean
    public ApiClient paymentApiClient(WebClient.Builder webClientBuilder, PaymentClientProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
                .responseTimeout(properties.responseTimeout());
        WebClient webClient = webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
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
