package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.PaymentServiceStub.Payment;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Корзина и заказы привязаны к покупателю: alice и bob работают с витриной одновременно
 * и видят и меняют только свои корзины и заказы, а оплата идёт с их собственных счетов.
 */
class UserDataIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private CartItemRepository cartItemRepository;

    private WebTestClient bobClient;
    private long ballId;
    private long racketId;

    @BeforeEach
    void setUp() {
        bobClient = clientOf(bob);
        ImageData image = new ImageData("image/png", new byte[]{1});
        ballId = itemService.createItem(new NewItem("Мяч", "Футбольный", 2500, image)).block();
        racketId = itemService.createItem(new NewItem("Ракетка", "Теннисная", 7800, image)).block();
    }

    @Test
    void cart_shouldBeSeparateForEachUser() {
        addToCart(webTestClient, ballId);
        addToCart(webTestClient, ballId);
        addToCart(bobClient, racketId);

        assertThat(getHtml(webTestClient, "/cart/items"))
                .contains("Мяч", "Итого: 5000 руб.")
                .doesNotContain("Ракетка");
        assertThat(getHtml(bobClient, "/cart/items"))
                .contains("Ракетка", "Итого: 7800 руб.")
                .doesNotContain("Мяч");
        assertThat(getHtml(webTestClient, "/items/" + ballId)).contains("<span>2</span>");
        assertThat(getHtml(bobClient, "/items/" + ballId)).contains("<span>0</span>");
    }

    @Test
    void userCanNotChangeCartOfAnotherUser() {
        addToCart(webTestClient, ballId);

        bobClient.post().uri("/cart/items")
                .body(BodyInserters.fromFormData("id", String.valueOf(ballId)).with("action", "DELETE"))
                .exchange()
                .expectStatus().isSeeOther();

        assertThat(getHtml(webTestClient, "/cart/items")).contains("Мяч", "Итого: 2500 руб.");
    }

    @Test
    void purchase_shouldUseOnlyBuyersCartAndAccount() {
        addToCart(webTestClient, ballId);
        addToCart(bobClient, racketId);

        long aliceOrderId = buy(webTestClient);

        assertThat(paymentServiceStub.getPayments()).containsExactly(new Payment("alice", 2500));
        StepVerifier.create(cartItemRepository.countByUserId(alice.getId())).expectNext(0L).verifyComplete();
        assertThat(getHtml(bobClient, "/cart/items")).contains("Ракетка", "Итого: 7800 руб.");
        assertThat(getHtml(webTestClient, "/orders")).contains("href=\"/orders/" + aliceOrderId + "\"");
    }

    @Test
    void orders_shouldBeVisibleOnlyToTheirOwner() {
        addToCart(webTestClient, ballId);
        long aliceOrderId = buy(webTestClient);
        addToCart(bobClient, racketId);
        long bobOrderId = buy(bobClient);

        assertThat(getHtml(bobClient, "/orders"))
                .contains("href=\"/orders/" + bobOrderId + "\"")
                .doesNotContain("href=\"/orders/" + aliceOrderId + "\"");
        bobClient.get().uri("/orders/{id}", aliceOrderId)
                .exchange()
                .expectStatus().isNotFound();
        assertThat(getHtml(webTestClient, "/orders/" + aliceOrderId)).contains("Мяч");
    }

    @Test
    void cartButton_shouldDependOnBalanceOfCurrentUser() {
        paymentServiceStub.setBalance("bob", 100);
        addToCart(webTestClient, ballId);
        addToCart(bobClient, ballId);

        assertThat(getHtml(webTestClient, "/cart/items")).doesNotContain("disabled");
        assertThat(getHtml(bobClient, "/cart/items")).contains("На счёте: 100 руб.", "disabled=\"disabled\"");
    }

    private static void addToCart(WebTestClient client, long itemId) {
        client.post().uri("/items/{id}", itemId)
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().isSeeOther();
    }

    private static long buy(WebTestClient client) {
        URI location = client.post().uri("/buy")
                .exchange()
                .expectStatus().isSeeOther()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getLocation();
        assertThat(location).isNotNull();
        return Long.parseLong(location.getPath().substring("/orders/".length()));
    }

    private static String getHtml(WebTestClient client, String uri) {
        return client.get().uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
