package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.Checkout;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.exception.PaymentServiceUnavailableException;
import ru.yandex.practicum.mymarket.model.CartLine;

/**
 * Покупка: оплата суммы корзины через сервис платежей и перенос корзины в новый заказ.
 */
@Service
public class PurchaseService {

    private final CartService cartService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public PurchaseService(CartService cartService, OrderService orderService, PaymentService paymentService) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    /**
     * Проверяет по балансу в сервисе платежей, можно ли оплатить корзину на сумму {@code total}.
     * Недоступность сервиса платежей — не ошибка страницы, а состояние, о котором сообщается пользователю.
     */
    public Mono<Checkout> getCheckout(long total) {
        if (total <= 0) {
            return Mono.just(Checkout.emptyCart());
        }
        return paymentService.getBalance()
                .map(balance -> Checkout.forBalance(balance, total))
                .onErrorResume(PaymentServiceUnavailableException.class, error -> Mono.just(Checkout.unavailable()));
    }

    /**
     * Списывает сумму корзины в сервисе платежей и только после успешной оплаты оформляет заказ и очищает корзину.
     * Если платёж отклонён или сервис недоступен, заказ не создаётся и корзина не меняется.
     *
     * @return id нового заказа
     */
    @Transactional
    public Mono<Long> buy() {
        return cartService.getCartLines()
                .collectList()
                .filter(cartLines -> !cartLines.isEmpty())
                .switchIfEmpty(Mono.error(EmptyCartException::new))
                .flatMap(cartLines -> paymentService.pay(CartLine.total(cartLines))
                        .then(Mono.defer(() -> orderService.createOrder(cartLines))))
                .flatMap(orderId -> cartService.clear().thenReturn(orderId));
    }
}
