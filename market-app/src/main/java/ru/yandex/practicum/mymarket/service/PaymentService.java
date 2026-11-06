package ru.yandex.practicum.mymarket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.exception.InsufficientFundsException;
import ru.yandex.practicum.mymarket.exception.PaymentAuthorizationException;
import ru.yandex.practicum.mymarket.exception.PaymentServiceUnavailableException;
import ru.yandex.practicum.mymarket.payment.client.api.BalanceApi;
import ru.yandex.practicum.mymarket.payment.client.api.PaymentsApi;
import ru.yandex.practicum.mymarket.payment.client.model.Balance;
import ru.yandex.practicum.mymarket.payment.client.model.ErrorResponse;
import ru.yandex.practicum.mymarket.payment.client.model.PaymentRequest;
import ru.yandex.practicum.mymarket.payment.client.model.PaymentResponse;

/**
 * Обращения к счёту покупателя в сервисе платежей через сгенерированный по OpenAPI клиент.
 * Счёт определяется логином покупателя. Токен сервера авторизации к запросам добавляет
 * {@link ru.yandex.practicum.mymarket.config.ClientCredentialsExchangeFilter}.
 * Ошибки HTTP и сети переводятся в исключения предметной области:
 * {@link InsufficientFundsException} — платёж отклонён (402),
 * {@link PaymentAuthorizationException} — витрине не выдали токен или сервис платежей его не принял (401/403),
 * {@link PaymentServiceUnavailableException} — всё остальное.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    static final String INSUFFICIENT_FUNDS_MESSAGE = "Недостаточно средств на счёте";

    private final BalanceApi balanceApi;
    private final PaymentsApi paymentsApi;

    public PaymentService(BalanceApi balanceApi, PaymentsApi paymentsApi) {
        this.balanceApi = balanceApi;
        this.paymentsApi = paymentsApi;
    }

    public Mono<Long> getBalance(String username) {
        return balanceApi.getBalance(username)
                .map(Balance::getAmount)
                .onErrorMap(PaymentService::toUnavailable);
    }

    /**
     * Списывает сумму со счёта покупателя.
     *
     * @return баланс после списания
     */
    public Mono<Long> pay(String username, long amount) {
        return paymentsApi.makePayment(username, new PaymentRequest().amount(amount))
                .map(PaymentResponse::getBalance)
                .onErrorMap(PaymentService::isPaymentDeclined, PaymentService::toInsufficientFunds)
                .onErrorMap(error -> !(error instanceof InsufficientFundsException), PaymentService::toUnavailable);
    }

    private static boolean isPaymentDeclined(Throwable error) {
        return error instanceof WebClientResponseException response
                && response.getStatusCode().isSameCodeAs(HttpStatus.PAYMENT_REQUIRED);
    }

    private static Throwable toInsufficientFunds(Throwable error) {
        ErrorResponse body = ((WebClientResponseException) error).getResponseBodyAs(ErrorResponse.class);
        return new InsufficientFundsException(body == null ? INSUFFICIENT_FUNDS_MESSAGE : body.getMessage());
    }

    private static Throwable toUnavailable(Throwable error) {
        if (isAuthorizationFailure(error)) {
            log.warn("Market app is not authorized to call payment service: {}", error.toString());
            return new PaymentAuthorizationException(error);
        }
        log.warn("Payment service request failed: {}", error.toString());
        return new PaymentServiceUnavailableException(error);
    }

    private static boolean isAuthorizationFailure(Throwable error) {
        if (error instanceof OAuth2AuthorizationException) {
            return true;
        }
        return error instanceof WebClientResponseException response
                && (response.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                || response.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN));
    }
}
