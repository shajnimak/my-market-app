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
import ru.yandex.practicum.mymarket.dto.Checkout;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.exception.InsufficientFundsException;
import ru.yandex.practicum.mymarket.exception.PaymentAuthorizationException;
import ru.yandex.practicum.mymarket.exception.PaymentServiceUnavailableException;
import ru.yandex.practicum.mymarket.model.CartLine;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.cartLine;
import static ru.yandex.practicum.mymarket.TestData.item;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private CartService cartService;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PurchaseService purchaseService;

    private static final long USER_ID = 10;
    private static final String USERNAME = "alice";

    private final List<CartLine> cartLines = List.of(
            cartLine(item(1, "Мяч", 2500), 2),
            cartLine(item(2, "Ракетка", 7800), 1)
    );

    @Test
    void buy_shouldPayCartTotal_thenCreateOrderAndClearCart() {
        when(cartService.getCartLines(USER_ID)).thenReturn(Flux.fromIterable(cartLines));
        when(paymentService.pay(USERNAME, 12_800)).thenReturn(Mono.just(87_200L));
        when(orderService.createOrder(USER_ID, cartLines)).thenReturn(Mono.just(7L));
        when(cartService.clear(USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(purchaseService.buy(USER_ID, USERNAME))
                .expectNext(7L)
                .verifyComplete();

        InOrder inOrder = inOrder(paymentService, orderService, cartService);
        inOrder.verify(paymentService).pay(USERNAME, 12_800);
        inOrder.verify(orderService).createOrder(USER_ID, cartLines);
        inOrder.verify(cartService).clear(USER_ID);
    }

    @Test
    void buy_shouldNotCreateOrder_whenPaymentDeclined() {
        when(cartService.getCartLines(USER_ID)).thenReturn(Flux.fromIterable(cartLines));
        when(paymentService.pay(USERNAME, 12_800)).thenReturn(Mono.error(new InsufficientFundsException("Недостаточно средств")));

        StepVerifier.create(purchaseService.buy(USER_ID, USERNAME))
                .expectError(InsufficientFundsException.class)
                .verify();

        verify(orderService, never()).createOrder(anyLong(), any());
        verify(cartService, never()).clear(anyLong());
    }

    @Test
    void buy_shouldNotCreateOrder_whenPaymentServiceUnavailable() {
        when(cartService.getCartLines(USER_ID)).thenReturn(Flux.fromIterable(cartLines));
        when(paymentService.pay(USERNAME, 12_800))
                .thenReturn(Mono.error(new PaymentServiceUnavailableException(new RuntimeException("down"))));

        StepVerifier.create(purchaseService.buy(USER_ID, USERNAME))
                .expectError(PaymentServiceUnavailableException.class)
                .verify();

        verify(orderService, never()).createOrder(anyLong(), any());
        verify(cartService, never()).clear(anyLong());
    }

    @Test
    void buy_shouldFailWithoutPayment_whenCartIsEmpty() {
        when(cartService.getCartLines(USER_ID)).thenReturn(Flux.empty());

        StepVerifier.create(purchaseService.buy(USER_ID, USERNAME))
                .expectError(EmptyCartException.class)
                .verify();

        verify(paymentService, never()).pay(anyString(), anyLong());
        verify(orderService, never()).createOrder(anyLong(), any());
        verify(cartService, never()).clear(anyLong());
    }

    @Test
    void getCheckout_shouldBeAvailable_whenBalanceCoversTotal() {
        when(paymentService.getBalance(USERNAME)).thenReturn(Mono.just(12_800L));

        StepVerifier.create(purchaseService.getCheckout(USERNAME, 12_800))
                .expectNext(new Checkout(Checkout.Status.AVAILABLE, 12_800L))
                .verifyComplete();
    }

    @Test
    void getCheckout_shouldReportInsufficientFunds_whenBalanceBelowTotal() {
        when(paymentService.getBalance(USERNAME)).thenReturn(Mono.just(12_799L));

        StepVerifier.create(purchaseService.getCheckout(USERNAME, 12_800))
                .expectNext(new Checkout(Checkout.Status.INSUFFICIENT_FUNDS, 12_799L))
                .verifyComplete();
    }

    @Test
    void getCheckout_shouldReportUnavailable_whenPaymentServiceFails() {
        when(paymentService.getBalance(USERNAME))
                .thenReturn(Mono.error(new PaymentServiceUnavailableException(new RuntimeException("down"))));

        StepVerifier.create(purchaseService.getCheckout(USERNAME, 12_800))
                .expectNext(Checkout.unavailable())
                .verifyComplete();
    }

    @Test
    void getCheckout_shouldReportUnavailable_whenMarketNotAuthorizedInPaymentService() {
        when(paymentService.getBalance(USERNAME))
                .thenReturn(Mono.error(new PaymentAuthorizationException(new RuntimeException("invalid_client"))));

        StepVerifier.create(purchaseService.getCheckout(USERNAME, 12_800))
                .expectNext(Checkout.unavailable())
                .verifyComplete();
    }

    @Test
    void getCheckout_shouldNotCallPaymentService_whenCartEmpty() {
        StepVerifier.create(purchaseService.getCheckout(USERNAME, 0))
                .expectNext(Checkout.emptyCart())
                .verifyComplete();

        verifyNoInteractions(paymentService);
    }
}
