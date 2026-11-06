package ru.yandex.practicum.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import ru.yandex.practicum.payment.api.BalanceApi;
import ru.yandex.practicum.payment.api.PaymentsApi;
import ru.yandex.practicum.payment.config.AccountProperties;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private BalanceApi balanceApi;

    @Autowired
    private PaymentsApi paymentsApi;

    @Autowired
    private AccountProperties accountProperties;

    @Autowired
    private SecurityWebFilterChain securityWebFilterChain;

    @Autowired
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
        assertThat(balanceApi).isNotNull();
        assertThat(paymentsApi).isNotNull();
        assertThat(securityWebFilterChain).isNotNull();
        assertThat(jwtDecoder).isNotNull();
        assertThat(accountProperties.initialBalance()).isEqualTo(50_000);
        assertThat(accountProperties.initialBalanceOf("poor")).isEqualTo(100);
    }
}
