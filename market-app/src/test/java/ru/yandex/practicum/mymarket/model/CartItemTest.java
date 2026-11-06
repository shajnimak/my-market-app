package ru.yandex.practicum.mymarket.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.yandex.practicum.mymarket.TestData.cartItem;

class CartItemTest {

    @Test
    void decrement_shouldReduceQuantity_whenMoreThanOne() {
        CartItem cartItem = cartItem(10, 1, 3);

        assertThat(cartItem.decrement()).isTrue();
        assertThat(cartItem.getQuantity()).isEqualTo(2);
    }

    @Test
    void decrement_shouldKeepLastUnitAndSignalRemoval() {
        CartItem cartItem = cartItem(10, 1, 1);

        assertThat(cartItem.decrement()).isFalse();
        assertThat(cartItem.getQuantity()).isEqualTo(1);
    }

    @Test
    void newCartItem_shouldStartFromOne() {
        CartItem cartItem = new CartItem(10L, 1L);
        cartItem.increment();

        assertThat(cartItem.getQuantity()).isEqualTo(2);
        assertThat(cartItem.getUserId()).isEqualTo(10L);
        assertThat(cartItem.getItemId()).isEqualTo(1L);
    }
}
