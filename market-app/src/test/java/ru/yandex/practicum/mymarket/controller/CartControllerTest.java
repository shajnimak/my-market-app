package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.CartAction;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartControllerTest extends AbstractControllerTest {

    private static final ItemDto BALL = new ItemDto(1, "Мяч", "Футбольный", "images/1", 2500, 2);

    @Test
    void getCart_shouldRenderItemsAndTotal() {
        when(cartService.getCart()).thenReturn(Mono.just(new CartDto(List.of(BALL), 5000)));

        String html = getHtml("/cart/items");

        assertThat(html).contains("Мяч", "2500 руб.", "<span>2</span>", "Итого: 5000 руб.", "action=\"/buy\"");
    }

    @Test
    void getCart_shouldHideBuyButton_whenCartEmpty() {
        when(cartService.getCart()).thenReturn(Mono.just(new CartDto(List.of(), 0)));

        assertThat(getHtml("/cart/items")).doesNotContain("action=\"/buy\"");
    }

    @Test
    void changeCartQuantity_shouldApplyActionAndRedirectToCart() {
        when(cartService.changeQuantity(1, CartAction.DELETE)).thenReturn(Mono.empty());

        webTestClient.post().uri("/cart/items")
                .body(BodyInserters.fromFormData("id", "1").with("action", "DELETE"))
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().location("/cart/items");

        verify(cartService).changeQuantity(1, CartAction.DELETE);
        verify(cartService, never()).getCart();
    }

    @Test
    void changeCartQuantity_shouldReturnBadRequest_whenIdMissing() {
        webTestClient.post().uri("/cart/items")
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().isBadRequest();

        verify(cartService, never()).changeQuantity(anyLong(), any());
    }
}
