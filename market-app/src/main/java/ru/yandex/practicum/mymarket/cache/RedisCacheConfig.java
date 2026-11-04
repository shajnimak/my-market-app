package ru.yandex.practicum.mymarket.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.ItemCard;

/**
 * Кеши товаров в Redis. Подключение к Redis ({@link ReactiveRedisConnectionFactory} на Lettuce) настраивает
 * Spring Boot по свойствам {@code spring.data.redis.*}; здесь для каждого типа значения создаётся
 * {@link ReactiveRedisTemplate} со строковыми ключами и JSON-значениями через {@link ObjectMapper} приложения.
 */
@Configuration
@EnableConfigurationProperties(ItemCacheProperties.class)
public class RedisCacheConfig {

    static final String KEY_PREFIX = "market:items:";

    @Bean
    public ReactiveRedisCache<ItemCard> itemCardCache(ReactiveRedisConnectionFactory connectionFactory,
                                                      ObjectMapper objectMapper,
                                                      ItemCacheProperties properties) {
        return new ReactiveRedisCache<>(template(connectionFactory, objectMapper, ItemCard.class),
                KEY_PREFIX + "card:", properties.ttl());
    }

    @Bean
    public ReactiveRedisCache<CatalogPage> catalogPageCache(ReactiveRedisConnectionFactory connectionFactory,
                                                            ObjectMapper objectMapper,
                                                            ItemCacheProperties properties) {
        return new ReactiveRedisCache<>(template(connectionFactory, objectMapper, CatalogPage.class),
                KEY_PREFIX + "page:", properties.ttl());
    }

    @Bean
    public ReactiveRedisCache<ImageData> itemImageCache(ReactiveRedisConnectionFactory connectionFactory,
                                                        ObjectMapper objectMapper,
                                                        ItemCacheProperties properties) {
        return new ReactiveRedisCache<>(template(connectionFactory, objectMapper, ImageData.class),
                KEY_PREFIX + "image:", properties.ttl());
    }

    private static <V> ReactiveRedisTemplate<String, V> template(ReactiveRedisConnectionFactory connectionFactory,
                                                                 ObjectMapper objectMapper,
                                                                 Class<V> valueType) {
        RedisSerializationContext<String, V> context = RedisSerializationContext
                .<String, V>newSerializationContext(StringRedisSerializer.UTF_8)
                .value(new Jackson2JsonRedisSerializer<>(objectMapper, valueType))
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
