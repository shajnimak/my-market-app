package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.Checkout;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.exception.PaymentServiceUnavailableException;
import ru.yandex.practicum.mymarket.model.CartLine;

/**
 * Покупка: оплата суммы корзины покупателя с его счёта в сервисе платежей и перенос корзины в новый заказ.
 * Покупатель передаётся двумя идентификаторами: id — для корзины и заказов в базе витрины,
 * логин — для счёта в сервисе платежей.
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
     * Проверяет по балансу счёта покупателя в сервисе платежей, можно ли оплатить корзину на сумму {@code total}.
     * Недоступность сервиса платежей — не ошибка страницы, а состояние, о котором сообщается пользователю.
     */
    public Mono<Checkout> getCheckout(String username, long total) {
        if (total <= 0) {
            return Mono.just(Checkout.emptyCart());
        }
        return paymentService.getBalance(username)
                .map(balance -> Checkout.forBalance(balance, total))
                .onErrorResume(PaymentServiceUnavailableException.class, error -> Mono.just(Checkout.unavailable()));
    }

    /**
     * Списывает сумму корзины покупателя с его счёта и только после успешной оплаты оформляет заказ
     * и очищает корзину. Если платёж отклонён или сервис недоступен, заказ не создаётся и корзина не меняется.
     *
     * @param userId   id покупателя в витрине
     * @param username логин покупателя — по нему сервис платежей находит счёт
     * @return id нового заказа
     */
    @Transactional
    public Mono<Long> buy(long userId, String username) {
        return cartService.getCartLines(userId)
                .collectList()
                .filter(cartLines -> !cartLines.isEmpty())
                .switchIfEmpty(Mono.error(EmptyCartException::new))
                .flatMap(cartLines -> paymentService.pay(username, CartLine.total(cartLines))
                        .then(Mono.defer(() -> orderService.createOrder(userId, cartLines))))
                .flatMap(orderId -> cartService.clear(userId).thenReturn(orderId));
    }
}
