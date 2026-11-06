package ru.yandex.practicum.mymarket.config;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Добавляет к запросам в сервис платежей access-токен, полученный по Client Credentials Flow.
 * <p>
 * Токен выдаётся витрине как приложению, а не покупателю, поэтому он запрашивается от имени самого клиента
 * (principal — имя регистрации клиента), а не от имени текущего покупателя: один токен на всё приложение,
 * а не по токену на каждого покупателя. {@link ReactiveOAuth2AuthorizedClientManager} хранит токен
 * в {@link ReactiveOAuth2AuthorizedClientService} и сам запрашивает новый, когда срок действия текущего истекает.
 * <p>
 * Если сервис платежей всё же отвечает 401 (например, сервер авторизации перезапущен и сменил ключ подписи),
 * сохранённый токен удаляется, и следующий запрос получит новый.
 * Если сервер авторизации отказывает в токене, запрос в сервис платежей не отправляется вовсе:
 * ошибка {@code ClientAuthorizationException} уходит вызывающему коду.
 */
public class ClientCredentialsExchangeFilter implements ExchangeFilterFunction {

    private final ReactiveOAuth2AuthorizedClientManager authorizedClientManager;
    private final ReactiveOAuth2AuthorizedClientService authorizedClientService;
    private final String clientRegistrationId;
    private final String principalName;

    public ClientCredentialsExchangeFilter(ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
                                           ReactiveOAuth2AuthorizedClientService authorizedClientService,
                                           String clientRegistrationId,
                                           String principalName) {
        this.authorizedClientManager = authorizedClientManager;
        this.authorizedClientService = authorizedClientService;
        this.clientRegistrationId = clientRegistrationId;
        this.principalName = principalName;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistrationId)
                .principal(principalName)
                .build();
        return authorizedClientManager.authorize(authorizeRequest)
                .map(authorizedClient -> ClientRequest.from(request)
                        .headers(headers -> headers.setBearerAuth(authorizedClient.getAccessToken().getTokenValue()))
                        .build())
                .flatMap(next::exchange)
                .flatMap(this::forgetRejectedToken);
    }

    private Mono<ClientResponse> forgetRejectedToken(ClientResponse response) {
        if (!response.statusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return Mono.just(response);
        }
        return authorizedClientService.removeAuthorizedClient(clientRegistrationId, principalName)
                .thenReturn(response);
    }
}
