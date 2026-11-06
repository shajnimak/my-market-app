package ru.yandex.practicum.mymarket.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.TestcontainersConfiguration;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemCard;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Слайс Spring Data Redis: только подключение к Redis из контейнера и кеши из {@link RedisCacheConfig},
 * без базы данных и веб-слоя.
 */
@DataRedisTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({TestcontainersConfiguration.class, RedisCacheConfig.class})
@TestPropertySource(properties = "market.cache.ttl=90s")
class ReactiveRedisCacheTest {

    private static final ItemCard BALL = new ItemCard(1, "Мяч", "Футбольный", "images/1", 2500);

    @Autowired
    private ReactiveRedisCache<ItemCard> itemCardCache;

    @Autowired
    private ReactiveRedisCache<CatalogPage> catalogPageCache;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private final AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    void flushRedis() {
        redisTemplate.execute(connection -> connection.serverCommands().flushDb()).blockLast();
    }

    @Test
    void get_shouldLoadOnMiss_andServeFromRedisOnHit() {
        StepVerifier.create(itemCardCache.get("1", () -> load(BALL))).expectNext(BALL).verifyComplete();
        StepVerifier.create(itemCardCache.get("1", () -> load(BALL))).expectNext(BALL).verifyComplete();

        assertThat(loads).hasValue(1);
    }

    @Test
    void get_shouldStoreValueAsJsonWithConfiguredTtl() {
        itemCardCache.get("1", () -> load(BALL)).block();

        String key = itemCardCache.redisKey("1");
        assertThat(key).isEqualTo("market:items:card:1");
        assertThat(redisTemplate.opsForValue().get(key).block())
                .isEqualTo("{\"id\":1,\"title\":\"Мяч\",\"description\":\"Футбольный\",\"imgPath\":\"images/1\",\"price\":2500}");
        Duration ttl = redisTemplate.getExpire(key).block();
        assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void get_shouldNotCacheEmptyResultOrError() {
        StepVerifier.create(itemCardCache.get("2", Mono::empty)).verifyComplete();
        StepVerifier.create(itemCardCache.get("3", () -> Mono.error(new IllegalStateException("boom"))))
                .expectError(IllegalStateException.class)
                .verify();

        StepVerifier.create(redisTemplate.hasKey(itemCardCache.redisKey("2"))).expectNext(false).verifyComplete();
        StepVerifier.create(redisTemplate.hasKey(itemCardCache.redisKey("3"))).expectNext(false).verifyComplete();
    }

    @Test
    void get_shouldFallBackToLoader_whenCachedValueUnreadable() {
        redisTemplate.opsForValue().set(itemCardCache.redisKey("1"), "not json").block();

        StepVerifier.create(itemCardCache.get("1", () -> load(BALL))).expectNext(BALL).verifyComplete();

        assertThat(loads).hasValue(1);
    }

    @Test
    void evictAll_shouldRemoveOnlyKeysOfThisCache() {
        CatalogPage page = new CatalogPage(List.of(), 1, 5, 0);
        catalogPageCache.get("NO:1:5:", () -> Mono.just(page)).block();
        catalogPageCache.get("PRICE:1:5:мяч", () -> Mono.just(page)).block();
        itemCardCache.get("1", () -> load(BALL)).block();

        StepVerifier.create(catalogPageCache.evictAll()).expectNext(2L).verifyComplete();

        StepVerifier.create(redisTemplate.keys("market:items:*"))
                .expectNext(itemCardCache.redisKey("1"))
                .verifyComplete();
    }

    private Mono<ItemCard> load(ItemCard card) {
        return Mono.fromSupplier(() -> {
            loads.incrementAndGet();
            return card;
        });
    }
}
