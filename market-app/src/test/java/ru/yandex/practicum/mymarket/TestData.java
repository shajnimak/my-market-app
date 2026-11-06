package ru.yandex.practicum.mymarket;

import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.cache.ReactiveRedisCache;
import ru.yandex.practicum.mymarket.dto.ItemCard;
import ru.yandex.practicum.mymarket.dto.ItemSummary;
import ru.yandex.practicum.mymarket.mapper.ItemMapper;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.CartLine;
import ru.yandex.practicum.mymarket.model.Item;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

public final class TestData {

    private TestData() {
    }

    public static Item item(long id, String title, long price) {
        Item item = new Item(title, "Описание " + title, price);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    public static ItemCard card(long id, String title, long price) {
        return ItemMapper.toCard(item(id, title, price));
    }

    public static ItemSummary summary(long id, String title, long price) {
        return ItemMapper.toSummary(item(id, title, price));
    }

    /**
     * Мок кеша, у которого всегда промах: {@code get} просто вызывает загрузчик.
     */
    @SuppressWarnings("unchecked")
    public static <V> ReactiveRedisCache<V> missingCache() {
        ReactiveRedisCache<V> cache = Mockito.mock(ReactiveRedisCache.class);
        lenient().when(cache.get(anyString(), any())).thenAnswer(invocation ->
                invocation.<Supplier<Mono<V>>>getArgument(1).get());
        lenient().when(cache.evictAll()).thenReturn(Mono.just(0L));
        return cache;
    }

    public static CartItem cartItem(long itemId, int quantity) {
        CartItem cartItem = new CartItem(itemId);
        for (int i = 1; i < quantity; i++) {
            cartItem.increment();
        }
        return cartItem;
    }

    public static CartLine cartLine(Item item, int quantity) {
        return new CartLine(item.getId(), item.getTitle(), item.getDescription(), item.getPrice(), quantity);
    }
}
