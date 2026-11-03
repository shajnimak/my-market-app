package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.ItemSort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemControllerTest extends AbstractControllerTest {

    private static final ItemDto BALL = new ItemDto(1, "Мяч", "Футбольный", "images/1", 2500, 0);
    private static final ItemDto RACKET = new ItemDto(2, "Ракетка", "Теннисная", "images/2", 7800, 2);
    private static final ItemDto SKI = new ItemDto(3, "Лыжи", "Беговые", "images/3", 12900, 0);
    private static final ItemDto SKATES = new ItemDto(4, "Коньки", "Фигурные", "images/4", 6400, 1);

    @Test
    void getItems_shouldUseDefaultsAndGroupItemsIntoRowsOfThree() {
        when(showcaseService.findItems("", ItemSort.NO, 1, 5))
                .thenReturn(Mono.just(new ItemsPage(List.of(BALL, RACKET, SKI, SKATES), new Paging(5, 1, false, true))));

        String html = getHtml("/");

        assertThat(html).contains("Мяч", "Ракетка", "Лыжи", "Коньки", "Страница: 1", "src=\"/images/2\"");
        assertThat(occurrences(html, "class=\"card\"")).isEqualTo(4);
        assertThat(occurrences(html, "<div>&nbsp;</div>")).isEqualTo(2);
        assertThat(html).contains("name=\"pageNumber\" value=\"2\"").doesNotContain("name=\"pageNumber\" value=\"0\"");
    }

    @Test
    void getItems_shouldPassRequestParametersToService() {
        when(showcaseService.findItems("мяч", ItemSort.PRICE, 2, 10))
                .thenReturn(Mono.just(new ItemsPage(List.of(BALL), new Paging(10, 2, true, false))));

        String html = getHtml("/items?search=мяч&sort=PRICE&pageNumber=2&pageSize=10");

        assertThat(html).contains(
                "value=\"мяч\"",
                "<option value=\"PRICE\" selected=\"selected\">",
                "<option value=\"10\" selected=\"selected\">",
                "Страница: 2",
                "name=\"pageNumber\" value=\"1\"");
    }

    @Test
    void getItems_shouldReturnBadRequest_whenSortUnknown() {
        webTestClient.get().uri("/items?sort=COLOR")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void changeCartQuantityFromItems_shouldChangeCartAndRedirectBackWithParams() {
        when(cartService.changeQuantity(2, CartAction.PLUS)).thenReturn(Mono.empty());

        webTestClient.post().uri("/items")
                .body(BodyInserters.fromFormData("id", "2")
                        .with("action", "PLUS")
                        .with("search", "мяч")
                        .with("sort", "ALPHA")
                        .with("pageNumber", "3")
                        .with("pageSize", "20"))
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().location("/items?search=%D0%BC%D1%8F%D1%87&sort=ALPHA&pageNumber=3&pageSize=20");

        verify(cartService).changeQuantity(2, CartAction.PLUS);
    }

    @Test
    void changeCartQuantityFromItems_shouldUseDefaultParams() {
        when(cartService.changeQuantity(2, CartAction.MINUS)).thenReturn(Mono.empty());

        webTestClient.post().uri("/items")
                .body(BodyInserters.fromFormData("id", "2").with("action", "MINUS"))
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().location("/items?search=&sort=NO&pageNumber=1&pageSize=5");
    }

    @Test
    void changeCartQuantityFromItems_shouldReturnBadRequest_whenActionMissing() {
        webTestClient.post().uri("/items")
                .body(BodyInserters.fromFormData("id", "2"))
                .exchange()
                .expectStatus().isBadRequest();

        verify(cartService, never()).changeQuantity(anyLong(), any());
    }

    @Test
    void getItem_shouldRenderItemPage() {
        when(showcaseService.getItem(2)).thenReturn(Mono.just(RACKET));

        String html = getHtml("/items/2");

        assertThat(html).contains("Ракетка", "Теннисная", "7800 руб.", "action=\"/items/2\"", "<span>2</span>");
    }

    @Test
    void getItem_shouldReturnNotFound_whenItemMissing() {
        when(showcaseService.getItem(99)).thenReturn(Mono.error(new ItemNotFoundException(99)));

        webTestClient.get().uri("/items/99")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void changeCartQuantityFromItem_shouldChangeCartAndRenderItemPage() {
        when(cartService.changeQuantity(2, CartAction.PLUS)).thenReturn(Mono.empty());
        when(showcaseService.getItem(2)).thenReturn(Mono.just(RACKET));

        webTestClient.post().uri("/items/2")
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Ракетка", "<span>2</span>"));

        var inOrder = inOrder(cartService, showcaseService);
        inOrder.verify(cartService).changeQuantity(2, CartAction.PLUS);
        inOrder.verify(showcaseService).getItem(2);
    }

    @Test
    void changeCartQuantityFromItem_shouldReturnNotFound_whenItemMissing() {
        when(cartService.changeQuantity(99, CartAction.PLUS)).thenReturn(Mono.error(new ItemNotFoundException(99)));

        webTestClient.post().uri("/items/99")
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().isNotFound();
    }
}
