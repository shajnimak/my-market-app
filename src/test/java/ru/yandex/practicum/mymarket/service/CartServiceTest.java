package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.cartItem;
import static ru.yandex.practicum.mymarket.TestData.item;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private CartService cartService;

    private final Item ball = item(1, "Мяч", 2500);

    @Test
    void plus_shouldAddItemToCart_whenItemNotInCart() {
        when(cartItemRepository.findByItemId(1L)).thenReturn(Optional.empty());
        when(itemRepository.findById(1L)).thenReturn(Optional.of(ball));

        cartService.changeQuantity(1, CartAction.PLUS);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getItem()).isSameAs(ball);
        assertThat(captor.getValue().getQuantity()).isEqualTo(1);
    }

    @Test
    void plus_shouldIncrementQuantity_whenItemAlreadyInCart() {
        CartItem cartItem = cartItem(ball, 2);
        when(cartItemRepository.findByItemId(1L)).thenReturn(Optional.of(cartItem));

        cartService.changeQuantity(1, CartAction.PLUS);

        assertThat(cartItem.getQuantity()).isEqualTo(3);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void plus_shouldThrow_whenItemDoesNotExist() {
        when(cartItemRepository.findByItemId(42L)).thenReturn(Optional.empty());
        when(itemRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.changeQuantity(42, CartAction.PLUS))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void minus_shouldDecrementQuantity_whenMoreThanOneInCart() {
        CartItem cartItem = cartItem(ball, 3);
        when(cartItemRepository.findByItemId(1L)).thenReturn(Optional.of(cartItem));

        cartService.changeQuantity(1, CartAction.MINUS);

        assertThat(cartItem.getQuantity()).isEqualTo(2);
        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void minus_shouldRemoveItemFromCart_whenLastOneInCart() {
        CartItem cartItem = cartItem(ball, 1);
        when(cartItemRepository.findByItemId(1L)).thenReturn(Optional.of(cartItem));

        cartService.changeQuantity(1, CartAction.MINUS);

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void minus_shouldDoNothing_whenItemNotInCart() {
        when(cartItemRepository.findByItemId(1L)).thenReturn(Optional.empty());

        cartService.changeQuantity(1, CartAction.MINUS);

        verify(cartItemRepository, never()).delete(any());
        verifyNoInteractions(itemRepository);
    }

    @Test
    void delete_shouldRemoveItemFromCartRegardlessOfQuantity() {
        CartItem cartItem = cartItem(ball, 5);
        when(cartItemRepository.findByItemId(1L)).thenReturn(Optional.of(cartItem));

        cartService.changeQuantity(1, CartAction.DELETE);

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void getCart_shouldReturnItemsWithCountsAndTotal() {
        Item racket = item(2, "Ракетка", 7800);
        when(cartItemRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(cartItem(ball, 2), cartItem(racket, 1)));

        CartDto cart = cartService.getCart();

        assertThat(cart.items())
                .extracting(ItemDto::id, ItemDto::count, ItemDto::imgPath)
                .containsExactly(
                        tuple(1L, 2, "images/1"),
                        tuple(2L, 1, "images/2")
                );
        assertThat(cart.total()).isEqualTo(2 * 2500 + 7800);
    }

    @Test
    void getCounts_shouldMapItemIdToQuantity() {
        when(cartItemRepository.findByItemIdIn(List.of(1L, 2L))).thenReturn(List.of(cartItem(ball, 4)));

        assertThat(cartService.getCounts(List.of(1L, 2L))).containsExactlyEntriesOf(Map.of(1L, 4));
    }

    @Test
    void getCounts_shouldNotQueryRepository_whenNoIds() {
        assertThat(cartService.getCounts(List.of())).isEmpty();

        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void getCount_shouldReturnZero_whenItemNotInCart() {
        when(cartItemRepository.findByItemId(1L)).thenReturn(Optional.empty());

        assertThat(cartService.getCount(1)).isZero();
    }
}
