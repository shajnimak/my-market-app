package ru.yandex.practicum.payment.service;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import ru.yandex.practicum.payment.config.AccountProperties;
import ru.yandex.practicum.payment.exception.InsufficientFundsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountServiceTest {

    private final AccountService accountService =
            new AccountService(new AccountProperties(10_000, Map.of("bob", 500L)));

    @Test
    void getBalance_shouldOpenAccountWithInitialBalanceFromConfig() {
        StepVerifier.create(accountService.getBalance("alice"))
                .expectNext(10_000L)
                .verifyComplete();
    }

    @Test
    void getBalance_shouldUseBalanceConfiguredForUser() {
        StepVerifier.create(accountService.getBalance("bob"))
                .expectNext(500L)
                .verifyComplete();
    }

    @Test
    void withdraw_shouldSubtractAmountAndReturnNewBalance() {
        StepVerifier.create(accountService.withdraw("alice", 2_500))
                .expectNext(7_500L)
                .verifyComplete();
        StepVerifier.create(accountService.getBalance("alice"))
                .expectNext(7_500L)
                .verifyComplete();
    }

    @Test
    void withdraw_shouldChangeOnlyAccountOfThatUser() {
        StepVerifier.create(accountService.withdraw("alice", 2_500))
                .expectNext(7_500L)
                .verifyComplete();

        StepVerifier.create(accountService.getBalance("carol"))
                .expectNext(10_000L)
                .verifyComplete();
        StepVerifier.create(accountService.getBalance("bob"))
                .expectNext(500L)
                .verifyComplete();
    }

    @Test
    void withdraw_shouldAllowSpendingWholeBalance() {
        StepVerifier.create(accountService.withdraw("alice", 10_000))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void withdraw_shouldFailAndKeepBalance_whenFundsInsufficient() {
        StepVerifier.create(accountService.withdraw("bob", 501))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(InsufficientFundsException.class, e -> {
                            assertThat(e.getBalance()).isEqualTo(500);
                            assertThat(e.getAmount()).isEqualTo(501);
                        }))
                .verify();
        StepVerifier.create(accountService.getBalance("bob"))
                .expectNext(500L)
                .verifyComplete();
    }

    @Test
    void withdraw_shouldRejectNonPositiveAmount() {
        StepVerifier.create(accountService.withdraw("alice", 0))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void withdraw_shouldNeverMakeBalanceNegative_underConcurrentPayments() {
        long succeeded = Flux.range(0, 200)
                .parallel(8)
                .runOn(Schedulers.parallel())
                .flatMap(i -> accountService.withdraw("alice", 70)
                        .map(balance -> 1)
                        .onErrorResume(InsufficientFundsException.class, e -> Mono.empty()))
                .sequential()
                .count()
                .block();

        assertThat(succeeded).isEqualTo(10_000 / 70);
        assertThat(accountService.getBalance("alice").block()).isEqualTo(10_000 % 70);
    }
}
