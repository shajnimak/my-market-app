package ru.yandex.practicum.payment.service;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import ru.yandex.practicum.payment.config.AccountProperties;
import ru.yandex.practicum.payment.exception.InsufficientFundsException;

import static org.assertj.core.api.Assertions.assertThat;

class AccountServiceTest {

    private final AccountService accountService = new AccountService(new AccountProperties(10_000));

    @Test
    void getBalance_shouldReturnInitialBalanceFromConfig() {
        StepVerifier.create(accountService.getBalance())
                .expectNext(10_000L)
                .verifyComplete();
    }

    @Test
    void withdraw_shouldSubtractAmountAndReturnNewBalance() {
        StepVerifier.create(accountService.withdraw(2_500))
                .expectNext(7_500L)
                .verifyComplete();
        StepVerifier.create(accountService.getBalance())
                .expectNext(7_500L)
                .verifyComplete();
    }

    @Test
    void withdraw_shouldAllowSpendingWholeBalance() {
        StepVerifier.create(accountService.withdraw(10_000))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void withdraw_shouldFailAndKeepBalance_whenFundsInsufficient() {
        StepVerifier.create(accountService.withdraw(10_001))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(InsufficientFundsException.class, e -> {
                            assertThat(e.getBalance()).isEqualTo(10_000);
                            assertThat(e.getAmount()).isEqualTo(10_001);
                        }))
                .verify();
        StepVerifier.create(accountService.getBalance())
                .expectNext(10_000L)
                .verifyComplete();
    }

    @Test
    void withdraw_shouldRejectNonPositiveAmount() {
        StepVerifier.create(accountService.withdraw(0))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void withdraw_shouldNeverMakeBalanceNegative_underConcurrentPayments() {
        long succeeded = Flux.range(0, 200)
                .parallel(8)
                .runOn(Schedulers.parallel())
                .flatMap(i -> accountService.withdraw(70)
                        .map(balance -> 1)
                        .onErrorResume(InsufficientFundsException.class, e -> Mono.empty()))
                .sequential()
                .count()
                .block();

        assertThat(succeeded).isEqualTo(10_000 / 70);
        assertThat(accountService.getBalance().block()).isEqualTo(10_000 % 70);
    }
}
