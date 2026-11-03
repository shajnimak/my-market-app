package ru.yandex.practicum.mymarket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.ReactiveTransactionManager;
import ru.yandex.practicum.mymarket.config.DemoCatalogLoader;

import static org.assertj.core.api.Assertions.assertThat;

class MyMarketAppApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBeanNamesForType(DemoCatalogLoader.class)).isEmpty();
        assertThat(context.getBean(ReactiveTransactionManager.class)).isNotNull();
    }
}
