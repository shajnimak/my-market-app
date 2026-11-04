package ru.yandex.practicum.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Test
    void contextLoads() {
        assertThat(balanceApi).isNotNull();
        assertThat(paymentsApi).isNotNull();
        assertThat(accountProperties.initialBalance()).isEqualTo(50_000);
    }
}
