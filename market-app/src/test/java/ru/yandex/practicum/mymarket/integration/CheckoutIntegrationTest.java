package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.PaymentServiceStub.Payment;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.service.ItemService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Страница корзины и кнопка «Купить» покупателя alice в зависимости от ответа сервиса платежей (заглушки по HTTP).
 */
class CheckoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void putItemIntoCart() {
        long itemId = itemService.createItem(new NewItem("Ракетка", "Теннисная", 7800,
                new ImageData("image/png", new byte[]{1}))).block();
        webTestClient.post().uri("/items/{id}", itemId)
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().isSeeOther();
    }

    @Test
    void cartPage_shouldEnableBuyButton_whenBalanceCoversTotal() {
        paymentServiceStub.setBalance("alice", 7800);

        assertThat(getCartHtml())
                .contains("Итого: 7800 руб.", "На счёте: 7800 руб.", "<button class=\"btn btn-warning\">Купить</button>")
                .doesNotContain("disabled");
    }

    @Test
    void cartPage_shouldDisableBuyButton_whenBalanceBelowTotal() {
        paymentServiceStub.setBalance("alice", 7799);

        assertThat(getCartHtml())
                .contains("На счёте: 7799 руб.", "disabled=\"disabled\"", "Недостаточно средств на счёте");
    }

    @Test
    void cartPage_shouldDisableBuyButton_whenPaymentServiceUnavailable() {
        paymentServiceStub.setAvailable(false);

        assertThat(getCartHtml())
                .contains("disabled=\"disabled\"", "Сервис платежей недоступен")
                .doesNotContain("На счёте");
    }

    @Test
    void buy_shouldShowPaymentErrorAndKeepCart_whenPaymentDeclined() {
        paymentServiceStub.setBalance("alice", 100);

        String html = webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(html).contains("Недостаточно средств: баланс 100 руб., требуется 7800 руб.");
        StepVerifier.create(orderRepository.count()).expectNext(0L).verifyComplete();
        StepVerifier.create(cartItemRepository.count()).expectNext(1L).verifyComplete();
    }

    @Test
    void buy_shouldReturnServiceUnavailable_whenPaymentServiceDown() {
        paymentServiceStub.setAvailable(false);

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isEqualTo(503);

        StepVerifier.create(orderRepository.count()).expectNext(0L).verifyComplete();
    }

    @Test
    void buy_shouldWithdrawTotalAndRedirectToOrder_whenPaymentSucceeds() {
        paymentServiceStub.setBalance("alice", 10_000);

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isSeeOther();

        assertThat(paymentServiceStub.getPayments()).containsExactly(new Payment("alice", 7800));
        assertThat(paymentServiceStub.getBalance("alice")).isEqualTo(2200);
        StepVerifier.create(orderRepository.count()).expectNext(1L).verifyComplete();
    }

    private String getCartHtml() {
        return webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
