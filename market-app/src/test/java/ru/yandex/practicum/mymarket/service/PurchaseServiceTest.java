package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.model.CartLine;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.cartLine;
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
        List<CartLine> cartLines = List.of(cartLine(item(1, "Мяч", 2500), 2));
        when(cartService.getCartLines()).thenReturn(Flux.fromIterable(cartLines));
        when(orderService.createOrder(cartLines)).thenReturn(Mono.just(7L));
        when(cartService.clear()).thenReturn(Mono.empty());

        StepVerifier.create(purchaseService.buy())
                .expectNext(7L)
                .verifyComplete();

        InOrder inOrder = inOrder(orderService, cartService);
        inOrder.verify(orderService).createOrder(cartLines);
        inOrder.verify(cartService).clear();
    }

    @Test
    void buy_shouldFail_whenCartIsEmpty() {
        when(cartService.getCartLines()).thenReturn(Flux.empty());

        StepVerifier.create(purchaseService.buy())
                .expectError(EmptyCartException.class)
                .verify();

        verify(orderService, never()).createOrder(any());
        verify(cartService, never()).clear();
    }
}
