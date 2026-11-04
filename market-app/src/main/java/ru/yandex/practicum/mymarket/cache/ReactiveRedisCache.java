package ru.yandex.practicum.mymarket.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Кеш вида cache-aside поверх {@link ReactiveRedisTemplate}: значение ищется в Redis по ключу
 * с общим префиксом, при промахе загружается из источника (базы данных) и сохраняется с TTL.
 * Ошибки Redis не ломают запрос: значение просто загружается из источника, а кеш пропускается.
 *
 * @param <V> тип кешируемого значения, сериализуется в JSON
 */
public class ReactiveRedisCache<V> {

    private static final Logger log = LoggerFactory.getLogger(ReactiveRedisCache.class);

    private static final long SCAN_BATCH_SIZE = 500;

    private final ReactiveRedisTemplate<String, V> redisTemplate;
    private final String keyPrefix;
    private final Duration ttl;

    public ReactiveRedisCache(ReactiveRedisTemplate<String, V> redisTemplate, String keyPrefix, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
    }

    /**
     * Возвращает значение из кеша, а при промахе — из {@code loader}, сохраняя его в кеш.
     * Пустой результат и ошибки {@code loader} не кешируются.
     */
    public Mono<V> get(String key, Mono<V> loader) {
        String redisKey = redisKey(key);
        return redisTemplate.opsForValue().get(redisKey)
                .onErrorResume(error -> skipCache("read", redisKey, error))
                .switchIfEmpty(Mono.defer(() -> loader.flatMap(value -> put(redisKey, value).thenReturn(value))));
    }

    /**
     * Удаляет все записи этого кеша (все ключи с его префиксом).
     *
     * @return количество удалённых ключей
     */
    public Mono<Long> evictAll() {
        ScanOptions options = ScanOptions.scanOptions().match(keyPrefix + "*").count(SCAN_BATCH_SIZE).build();
        return redisTemplate.scan(options)
                .buffer((int) SCAN_BATCH_SIZE)
                .concatMap(keys -> redisTemplate.delete(keys.toArray(String[]::new)))
                .reduce(0L, Long::sum)
                .onErrorResume(error -> skipCache("evict", keyPrefix + "*", error).thenReturn(0L));
    }

    public String redisKey(String key) {
        return keyPrefix + key;
    }

    public Duration getTtl() {
        return ttl;
    }

    private Mono<Boolean> put(String redisKey, V value) {
        return redisTemplate.opsForValue().set(redisKey, value, ttl)
                .onErrorResume(error -> skipCache("write", redisKey, error));
    }

    private static <T> Mono<T> skipCache(String operation, String redisKey, Throwable error) {
        log.warn("Redis {} of {} failed, cache skipped: {}", operation, redisKey, error.toString());
        return Mono.empty();
    }
}
