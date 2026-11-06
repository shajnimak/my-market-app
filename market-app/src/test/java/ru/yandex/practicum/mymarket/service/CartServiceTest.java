package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.card;
import static ru.yandex.practicum.mymarket.TestData.cartItem;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final long USER_ID = 10;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ItemService itemService;

    @InjectMocks
    private CartService cartService;


    @Test
    void plus_shouldAddItemToCart_whenItemNotInCart() {
        when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L)).thenReturn(Mono.empty());
        when(itemService.getItem(1)).thenReturn(Mono.just(card(1, "Мяч", 2500)));
        when(cartItemRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(cartService.changeQuantity(USER_ID, 1, CartAction.PLUS)).verifyComplete();

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getItemId()).isEqualTo(1L);
        assertThat(captor.getValue().getQuantity()).isEqualTo(1);
    }

    @Test
    void plus_shouldIncrementQuantity_whenItemAlreadyInCart() {
        CartItem cartItem = cartItem(USER_ID, 1, 2);
        when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L)).thenReturn(Mono.just(cartItem));
        when(cartItemRepository.save(cartItem)).thenReturn(Mono.just(cartItem));

        StepVerifier.create(cartService.changeQuantity(USER_ID, 1, CartAction.PLUS)).verifyComplete();

        assertThat(cartItem.getQuantity()).isEqualTo(3);
        verifyNoInteractions(itemService);
    }

    @Test
    void plus_shouldFail_whenItemDoesNotExist() {
        when(cartItemRepository.findByUserIdAndItemId(USER_ID, 42L)).thenReturn(Mono.empty());
        when(itemService.getItem(42)).thenReturn(Mono.error(new ItemNotFoundException(42)));

        StepVerifier.create(cartService.changeQuantity(USER_ID, 42, CartAction.PLUS))
                .expectError(ItemNotFoundException.class)
                .verify();

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void minus_shouldDecrementQuantity_whenMoreThanOneInCart() {
        CartItem cartItem = cartItem(USER_ID, 1, 3);
        when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L)).thenReturn(Mono.just(cartItem));
        when(cartItemRepository.save(cartItem)).thenReturn(Mono.just(cartItem));

        StepVerifier.create(cartService.changeQuantity(USER_ID, 1, CartAction.MINUS)).verifyComplete();

        assertThat(cartItem.getQuantity()).isEqualTo(2);
        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void minus_shouldRemoveItemFromCart_whenLastOneInCart() {
        CartItem cartItem = cartItem(USER_ID, 1, 1);
        when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L)).thenReturn(Mono.just(cartItem));
        when(cartItemRepository.delete(cartItem)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeQuantity(USER_ID, 1, CartAction.MINUS)).verifyComplete();

        verify(cartItemRepository).delete(cartItem);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void minus_shouldDoNothing_whenItemNotInCart() {
        when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeQuantity(USER_ID, 1, CartAction.MINUS)).verifyComplete();

        verify(cartItemRepository, never()).delete(any());
        verify(cartItemRepository, never()).save(any());
        verifyNoInteractions(itemService);
    }

    @Test
    void delete_shouldRemoveItemFromCartRegardlessOfQuantity() {
        CartItem cartItem = cartItem(USER_ID, 1, 5);
        when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L)).thenReturn(Mono.just(cartItem));
        when(cartItemRepository.delete(cartItem)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeQuantity(USER_ID, 1, CartAction.DELETE)).verifyComplete();

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void getCart_shouldCombineCartQuantitiesWithCachedItemCards() {
        when(cartItemRepository.findByUserIdOrderByIdAsc(USER_ID)).thenReturn(Flux.just(cartItem(USER_ID, 1, 2), cartItem(USER_ID, 2, 1)));
        when(itemService.getItem(1)).thenReturn(Mono.just(card(1, "Мяч", 2500)));
        when(itemService.getItem(2)).thenReturn(Mono.just(card(2, "Ракетка", 7800)));

        StepVerifier.create(cartService.getCart(USER_ID))
                .assertNext(cart -> {
                    assertThat(cart.items())
                            .extracting(ItemDto::id, ItemDto::count, ItemDto::imgPath)
                            .containsExactly(
                                    tuple(1L, 2, "images/1"),
                                    tuple(2L, 1, "images/2")
                            );
                    assertThat(cart.total()).isEqualTo(2 * 2500 + 7800);
                })
                .verifyComplete();
    }

    @Test
    void getCart_shouldReturnEmptyCart_whenNothingAdded() {
        when(cartItemRepository.findByUserIdOrderByIdAsc(USER_ID)).thenReturn(Flux.empty());

        StepVerifier.create(cartService.getCart(USER_ID))
                .assertNext(cart -> {
                    assertThat(cart.items()).isEmpty();
                    assertThat(cart.total()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void getCounts_shouldMapItemIdToQuantity() {
        when(cartItemRepository.findByUserIdAndItemIdIn(USER_ID, List.of(1L, 2L))).thenReturn(Flux.just(cartItem(USER_ID, 1, 4)));

        StepVerifier.create(cartService.getCounts(USER_ID, List.of(1L, 2L)))
                .expectNext(Map.of(1L, 4))
                .verifyComplete();
    }

    @Test
    void getCounts_shouldNotQueryRepository_whenNoIds() {
        StepVerifier.create(cartService.getCounts(USER_ID, List.of()))
                .expectNext(Map.of())
                .verifyComplete();

        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void getCount_shouldReturnZero_whenItemNotInCart() {
        when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.getCount(USER_ID, 1))
                .expectNext(0)
                .verifyComplete();
    }

    @Test
    void clear_shouldDeleteOnlyCartItemsOfThatUser() {
        when(cartItemRepository.deleteByUserId(USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.clear(USER_ID)).verifyComplete();

        verify(cartItemRepository).deleteByUserId(USER_ID);
        verify(cartItemRepository, never()).deleteAll();
    }
}
