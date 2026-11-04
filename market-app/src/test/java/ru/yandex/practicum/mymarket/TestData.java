package ru.yandex.practicum.mymarket;

import org.springframework.test.util.ReflectionTestUtils;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.CartLine;
import ru.yandex.practicum.mymarket.model.Item;

public final class TestData {

    private TestData() {
    }

    public static Item item(long id, String title, long price) {
        Item item = new Item(title, "Описание " + title, price);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
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
