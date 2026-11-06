package ru.yandex.practicum.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.api.BalanceApi;
import ru.yandex.practicum.payment.api.model.Balance;
import ru.yandex.practicum.payment.service.AccountService;

/**
 * Реализация сгенерированного по OpenAPI интерфейса {@link BalanceApi}: маппинг и формат ответа заданы спецификацией.
 */
@RestController
public class BalanceController implements BalanceApi {

    private final AccountService accountService;

    public BalanceController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public Mono<ResponseEntity<Balance>> getBalance(ServerWebExchange exchange) {
        return accountService.getBalance()
                .map(amount -> ResponseEntity.ok(new Balance(amount)));
    }
}
