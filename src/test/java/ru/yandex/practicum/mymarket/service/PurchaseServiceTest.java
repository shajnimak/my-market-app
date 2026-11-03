package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.model.CartItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.cartItem;
import static ru.yandex.practicum.mymarket.TestData.item;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private CartService cartService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PurchaseService purchaseService;

    @Test
    void buy_shouldCreateOrderFromCartAndClearCart() {
        List<CartItem> cartItems = List.of(cartItem(item(1, "Мяч", 2500), 2));
        when(cartService.getCartItems()).thenReturn(cartItems);
        when(orderService.createOrder(cartItems)).thenReturn(15L);

        long orderId = purchaseService.buy();

        assertThat(orderId).isEqualTo(15);
        InOrder inOrder = inOrder(orderService, cartService);
        inOrder.verify(orderService).createOrder(cartItems);
        inOrder.verify(cartService).clear();
    }

    @Test
    void buy_shouldThrow_whenCartIsEmpty() {
        when(cartService.getCartItems()).thenReturn(List.of());

        assertThatThrownBy(() -> purchaseService.buy()).isInstanceOf(EmptyCartException.class);
        verify(orderService, never()).createOrder(any());
        verify(cartService, never()).clear();
    }
}
