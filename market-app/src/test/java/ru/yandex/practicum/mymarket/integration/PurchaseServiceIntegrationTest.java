package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.PaymentServiceStub;
import ru.yandex.practicum.mymarket.PaymentServiceStub.Payment;
import ru.yandex.practicum.mymarket.dto.Checkout;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.exception.InsufficientFundsException;
import ru.yandex.practicum.mymarket.exception.PaymentServiceUnavailableException;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PurchaseService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Покупка целиком: корзина из базы и кеша, оплата по HTTP в заглушке сервиса платежей, сохранение заказа.
 */
class PurchaseServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void buy_shouldPayCartTotal_persistOrderAndClearCart() {
        long itemId = createItem(1500);

        StepVerifier.create(cartService.changeQuantity(alice.getId(), itemId, CartAction.PLUS)
                        .then(cartService.changeQuantity(alice.getId(), itemId, CartAction.PLUS))
                        .then(purchaseService.buy(alice.getId(), "alice"))
                        .flatMap(orderId -> orderService.getOrder(alice.getId(), orderId)))
                .assertNext(order -> {
                    assertThat(order.totalSum()).isEqualTo(3000);
                    assertThat(order.items()).singleElement()
                            .satisfies(item -> {
                                assertThat(item.id()).isEqualTo(itemId);
                                assertThat(item.count()).isEqualTo(2);
                            });
                })
                .verifyComplete();
        StepVerifier.create(cartItemRepository.count())
                .expectNext(0L)
                .verifyComplete();
        assertThat(paymentServiceStub.getPayments()).containsExactly(new Payment("alice", 3000));
        assertThat(paymentServiceStub.getBalance("alice")).isEqualTo(PaymentServiceStub.DEFAULT_BALANCE - 3000);
    }

    @Test
    void buy_shouldKeepCartAndNotCreateOrder_whenPaymentDeclined() {
        long itemId = createItem(1500);
        paymentServiceStub.setBalance("alice", 1499);

        StepVerifier.create(cartService.changeQuantity(alice.getId(), itemId, CartAction.PLUS).then(purchaseService.buy(alice.getId(), "alice")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InsufficientFundsException.class)
                        .hasMessage("Недостаточно средств: баланс 1499 руб., требуется 1500 руб."))
                .verify();

        assertNothingBought();
        assertThat(paymentServiceStub.getBalance("alice")).isEqualTo(1499);
    }

    @Test
    void buy_shouldKeepCartAndNotCreateOrder_whenPaymentServiceUnavailable() {
        long itemId = createItem(1500);
        paymentServiceStub.setAvailable(false);

        StepVerifier.create(cartService.changeQuantity(alice.getId(), itemId, CartAction.PLUS).then(purchaseService.buy(alice.getId(), "alice")))
                .expectError(PaymentServiceUnavailableException.class)
                .verify();

        assertNothingBought();
    }

    @Test
    void getCheckout_shouldCompareCartTotalWithBalanceFromPaymentService() {
        paymentServiceStub.setBalance("alice", 3000);

        StepVerifier.create(purchaseService.getCheckout("alice", 3000))
                .expectNext(new Checkout(Checkout.Status.AVAILABLE, 3000L))
                .verifyComplete();
        StepVerifier.create(purchaseService.getCheckout("alice", 3001))
                .expectNext(new Checkout(Checkout.Status.INSUFFICIENT_FUNDS, 3000L))
                .verifyComplete();

        paymentServiceStub.setAvailable(false);
        StepVerifier.create(purchaseService.getCheckout("alice", 3000))
                .expectNext(Checkout.unavailable())
                .verifyComplete();
    }

    @Test
    void buy_shouldNotCreateOrder_whenCartEmpty() {
        StepVerifier.create(purchaseService.buy(alice.getId(), "alice"))
                .expectError(EmptyCartException.class)
                .verify();
        StepVerifier.create(orderRepository.count())
                .expectNext(0L)
                .verifyComplete();
    }

    private long createItem(long price) {
        return itemService.createItem(new NewItem("Мяч", "Описание", price,
                new ImageData("image/png", new byte[]{1}))).block();
    }

    private void assertNothingBought() {
        StepVerifier.create(orderRepository.count())
                .expectNext(0L)
                .verifyComplete();
        StepVerifier.create(cartItemRepository.count())
                .expectNext(1L)
                .verifyComplete();
        assertThat(paymentServiceStub.getPayments()).isEmpty();
    }
}
