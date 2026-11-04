package ru.yandex.practicum.mymarket;

import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Очистка таблиц между тестами. Реактивные тесты не откатывают транзакции автоматически,
 * поэтому каждый тест начинает с пустой базы.
 */
public final class TestDatabase {

    private static final String[] TABLES = {"order_items", "orders", "cart_items", "item_images", "items"};

    private TestDatabase() {
    }

    public static Mono<Void> clean(DatabaseClient databaseClient) {
        return Flux.fromArray(TABLES)
                .concatMap(table -> databaseClient.sql("delete from " + table).then())
                .then();
    }
}
