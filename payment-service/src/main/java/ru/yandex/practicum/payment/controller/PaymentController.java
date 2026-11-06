package ru.yandex.practicum.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.api.PaymentsApi;
import ru.yandex.practicum.payment.api.model.PaymentRequest;
import ru.yandex.practicum.payment.api.model.PaymentResponse;
import ru.yandex.practicum.payment.service.AccountService;

/**
 * Реализация сгенерированного по OpenAPI интерфейса {@link PaymentsApi}.
 * Тело запроса и логин проверяются Bean Validation по ограничениям из спецификации, ошибки переводит {@link ApiExceptionHandler}.
 * Доступ — только со скоупом {@code payments.write} (см. {@code SecurityConfig}).
 */
@RestController
public class PaymentController implements PaymentsApi {

    private final AccountService accountService;

    public PaymentController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public Mono<ResponseEntity<PaymentResponse>> makePayment(String username,
                                                             Mono<PaymentRequest> paymentRequest,
                                                             ServerWebExchange exchange) {
        return paymentRequest.flatMap(request -> accountService.withdraw(username, request.getAmount())
                .map(balance -> ResponseEntity.ok(new PaymentResponse(request.getAmount(), balance))));
    }
}
