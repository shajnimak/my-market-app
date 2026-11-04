package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.ShowcaseService;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сквозной сценарий через HTTP: витрина → корзина → покупка → заказы.
 */
class ShopFlowIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] GIF_CONTENT = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};

    @Autowired
    private ItemService itemService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ShowcaseService showcaseService;

    @Autowired
    private OrderService orderService;

    private long ballId;
    private long racketId;
    private long basketballId;

    @BeforeEach
    void setUp() {
        ImageData image = new ImageData("image/png", new byte[]{1, 2, 3});
        ballId = itemService.createItem(new NewItem("Футбольный мяч", "Размер 5", 2500, image)).block();
        racketId = itemService.createItem(new NewItem("Ракетка", "Теннисная", 7800, image)).block();
        basketballId = itemService.createItem(new NewItem("Баскетбольный мяч", "Для зала", 3200, image)).block();
    }

    @Test
    void itemsPage_shouldFilterSortAndPaginate() {
        String html = getHtml("/items?search=МЯЧ&sort=PRICE&pageSize=1&pageNumber=2");

        assertThat(html).contains("Баскетбольный мяч", "Страница: 2", "href=\"/items/" + basketballId + "\"")
                .doesNotContain("Футбольный мяч")
                .doesNotContain("Ракетка")
                .contains("name=\"pageNumber\" value=\"1\"")
                .doesNotContain("name=\"pageNumber\" value=\"3\"");
    }

    @Test
    void imageEndpoint_shouldServeStoredImage() {
        webTestClient.get().uri("/images/{id}", ballId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG)
                .expectBody(byte[].class).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void cart_shouldReflectChangesFromAllPages() {
        postForm("/items", "id", String.valueOf(ballId), "action", "PLUS");
        assertThat(postForm("/items/" + ballId, "action", "PLUS")).isEqualTo("/items/" + ballId);
        assertThat(getHtml("/items/" + ballId)).contains("<span>2</span>");
        assertThat(postForm("/cart/items", "id", String.valueOf(racketId), "action", "PLUS")).isEqualTo("/cart/items");
        assertThat(getHtml("/cart/items")).contains("Итого: " + (2 * 2500 + 7800) + " руб.");

        postForm("/cart/items", "id", String.valueOf(ballId), "action", "MINUS");
        assertThat(getHtml("/cart/items")).contains("Итого: " + (2500 + 7800) + " руб.");
        postForm("/cart/items", "id", String.valueOf(racketId), "action", "DELETE");
        assertThat(getHtml("/cart/items"))
                .contains("Итого: 2500 руб.")
                .doesNotContain("Ракетка");

        StepVerifier.create(showcaseService.getItem(ballId).map(ItemDto::count))
                .expectNext(1)
                .verifyComplete();
        assertThat(getHtml("/items/" + ballId)).contains("<span>1</span>");
    }

    @Test
    void buy_shouldCreateOrderFromCartAndEmptyCart() {
        postForm("/items/" + ballId, "action", "PLUS");
        postForm("/items/" + ballId, "action", "PLUS");
        postForm("/items/" + racketId, "action", "PLUS");

        URI location = webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isSeeOther()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getQuery()).isEqualTo("newOrder=true");
        long orderId = Long.parseLong(location.getPath().substring("/orders/".length()));

        OrderDto expectedOrder = new OrderDto(orderId, List.of(
                new OrderItemDto(ballId, "Футбольный мяч", 2500, 2),
                new OrderItemDto(racketId, "Ракетка", 7800, 1)
        ), 12800);
        StepVerifier.create(orderService.getOrder(orderId))
                .expectNext(expectedOrder)
                .verifyComplete();
        assertThat(getHtml(location.toString())).contains("Поздравляем", "Заказ №" + orderId, "Сумма: 12800 руб.");
        assertThat(getHtml("/orders")).contains("href=\"/orders/" + orderId + "\"", "Футбольный мяч (2 шт.) 5000 руб.");
        assertThat(paymentServiceStub.getPayments()).containsExactly(12800L);
        StepVerifier.create(cartService.getCart())
                .assertNext(cart -> {
                    assertThat(cart.items()).isEmpty();
                    assertThat(cart.total()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void buy_shouldFail_whenCartEmpty() {
        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void addItemForm_shouldCreateItemWithImage() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("title", "Ласты");
        builder.part("description", "Для плавания");
        builder.part("price", "1800");
        builder.part("image", GIF_CONTENT).filename("fins.gif").contentType(MediaType.IMAGE_GIF);

        URI location = webTestClient.post().uri("/admin/items")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isSeeOther()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getLocation();

        assertThat(location).isNotNull();
        assertThat(getHtml(location.toString())).contains("Ласты", "1800 руб.");
        webTestClient.get().uri(location.toString().replace("/items/", "/images/"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_GIF)
                .expectBody(byte[].class).isEqualTo(GIF_CONTENT);
    }

    private String getHtml(String uri) {
        return webTestClient.get().uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    /**
     * Отправляет форму и проверяет, что ответ — редирект (Post/Redirect/Get).
     *
     * @return адрес редиректа
     */
    private String postForm(String uri, String... keysAndValues) {
        var form = BodyInserters.fromFormData(keysAndValues[0], keysAndValues[1]);
        for (int i = 2; i < keysAndValues.length; i += 2) {
            form = form.with(keysAndValues[i], keysAndValues[i + 1]);
        }
        URI location = webTestClient.post().uri(uri)
                .body(form)
                .exchange()
                .expectStatus().isSeeOther()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getLocation();
        assertThat(location).isNotNull();
        return location.getPath();
    }
}
