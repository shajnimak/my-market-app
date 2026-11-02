package ru.yandex.practicum.mymarket;

import org.springframework.test.util.ReflectionTestUtils;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;

public final class TestData {

    private TestData() {
    }

    public static Item item(long id, String title, long price) {
        Item item = new Item(title, "Описание " + title, price);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    public static CartItem cartItem(Item item, int quantity) {
        CartItem cartItem = new CartItem(item);
        for (int i = 1; i < quantity; i++) {
            cartItem.increment();
        }
        return cartItem;
    }
}
