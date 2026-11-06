package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кеш товаров в Redis в работающем приложении. Чтобы отличить ответ из кеша от ответа из базы,
 * тесты меняют данные напрямую в базе в обход сервисов: пока запись в кеше жива, страницы показывают старые данные.
 */
class ItemCacheIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    private long ballId;

    @BeforeEach
    void setUp() {
        ballId = itemService.createItem(new NewItem("Футбольный мяч", "Размер 5", 2500,
                new ImageData("image/png", PNG))).block();
    }

    @Test
    void itemPage_shouldCacheCardWithTtl_andServeItFromRedis() {
        assertThat(getHtml("/items/" + ballId)).contains("Футбольный мяч", "2500 руб.");

        String key = "market:items:card:" + ballId;
        assertThat(redisTemplate.opsForValue().get(key).block()).contains("\"title\":\"Футбольный мяч\"");
        assertThat(redisTemplate.getExpire(key).block()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(2));

        renameInDatabase(ballId, "Волейбольный мяч");

        assertThat(getHtml("/items/" + ballId)).contains("Футбольный мяч").doesNotContain("Волейбольный мяч");
    }

    @Test
    void itemPage_shouldLoadFreshDataFromDatabase_afterCacheEntryExpires() {
        getHtml("/items/" + ballId);
        renameInDatabase(ballId, "Волейбольный мяч");

        redisTemplate.delete("market:items:card:" + ballId).block();

        assertThat(getHtml("/items/" + ballId)).contains("Волейбольный мяч");
    }

    @Test
    void itemsPage_shouldBeCachedPerQuery() {
        assertThat(getHtml("/items?search=мяч&sort=PRICE&pageSize=5")).contains("Футбольный мяч");

        assertThat(redisTemplate.hasKey("market:items:page:PRICE:1:5:мяч").block()).isTrue();
        renameInDatabase(ballId, "Волейбольный мяч");

        assertThat(getHtml("/items?search=МЯЧ&sort=PRICE&pageSize=5"))
                .contains("Футбольный мяч")
                .doesNotContain("Волейбольный мяч");
        assertThat(getHtml("/items?search=мяч&sort=ALPHA&pageSize=5")).contains("Волейбольный мяч");
    }

    @Test
    void itemsPage_shouldShowNewItem_becauseAddingItemEvictsCachedPages() {
        assertThat(getHtml("/items")).doesNotContain("Ракетка");

        itemRepository.save(new Item("Бадминтонная ракетка", "Без картинки", 900)).block();
        assertThat(getHtml("/items")).doesNotContain("Бадминтонная ракетка");

        itemService.createItem(new NewItem("Теннисная ракетка", "Для корта", 7800,
                new ImageData("image/png", PNG))).block();

        assertThat(redisTemplate.keys("market:items:page:*").collectList().block()).isEmpty();
        assertThat(getHtml("/items")).contains("Бадминтонная ракетка", "Теннисная ракетка");
    }

    @Test
    void imageEndpoint_shouldServeImageFromCache() {
        webTestClient.get().uri("/images/{id}", ballId).exchange().expectStatus().isOk();

        databaseClient.sql("delete from item_images where item_id = :id").bind("id", ballId).then().block();

        webTestClient.get().uri("/images/{id}", ballId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG)
                .expectBody(byte[].class).isEqualTo(PNG);
    }

    @Test
    void cartPage_shouldTakeItemDataFromCache() {
        webTestClient.post().uri("/items/{id}", ballId)
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().isSeeOther();
        databaseClient.sql("update items set title = 'Волейбольный мяч', price = 1 where id = :id")
                .bind("id", ballId)
                .then()
                .block();

        assertThat(getHtml("/cart/items"))
                .contains("Футбольный мяч", "Итого: 2500 руб.")
                .doesNotContain("Волейбольный мяч");
    }

    @Test
    void missingItem_shouldNotBeCached() {
        StepVerifier.create(itemService.getItem(ballId + 100))
                .expectError(ItemNotFoundException.class)
                .verify();

        assertThat(redisTemplate.hasKey("market:items:card:" + (ballId + 100)).block()).isFalse();
    }

    private void renameInDatabase(long itemId, String title) {
        databaseClient.sql("update items set title = :title where id = :id")
                .bind("title", title)
                .bind("id", itemId)
                .then()
                .block();
    }

    private String getHtml(String uri) {
        return webTestClient.get().uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
