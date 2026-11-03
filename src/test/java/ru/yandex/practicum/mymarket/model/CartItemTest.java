package ru.yandex.practicum.mymarket.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.yandex.practicum.mymarket.TestData.cartItem;
import static ru.yandex.practicum.mymarket.TestData.item;

class CartItemTest {

    private final Item ball = item(1, "Мяч", 2500);

    @Test
    void decrement_shouldReduceQuantity_whenMoreThanOne() {
        CartItem cartItem = cartItem(ball, 3);

        assertThat(cartItem.decrement()).isTrue();
        assertThat(cartItem.getQuantity()).isEqualTo(2);
    }

    @Test
    void decrement_shouldKeepLastUnitAndSignalRemoval() {
        CartItem cartItem = cartItem(ball, 1);

        assertThat(cartItem.decrement()).isFalse();
        assertThat(cartItem.getQuantity()).isEqualTo(1);
    }

    @Test
    void increment_shouldStartFromOneAndIncreaseTotalPrice() {
        CartItem cartItem = new CartItem(ball);
        cartItem.increment();

        assertThat(cartItem.getQuantity()).isEqualTo(2);
        assertThat(cartItem.getTotalPrice()).isEqualTo(5000);
    }
}
