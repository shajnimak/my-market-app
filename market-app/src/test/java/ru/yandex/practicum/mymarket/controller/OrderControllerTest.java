package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.exception.OrderNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.ALICE;
import static ru.yandex.practicum.mymarket.TestData.BOB;

class OrderControllerTest extends AbstractControllerTest {

    private static final OrderDto ORDER = new OrderDto(7, List.of(
            new OrderItemDto(1, "Мяч", 2500, 2),
            new OrderItemDto(2, "Ракетка", 7800, 1)
    ), 12800);

    @Test
    void getOrders_shouldRenderAllOrders() {
        OrderDto otherOrder = new OrderDto(8, List.of(new OrderItemDto(3, "Лыжи", 12900, 1)), 12900);
        when(orderService.getOrders(ALICE.getId())).thenReturn(Flux.just(otherOrder, ORDER));

        String html = getHtml("/orders");

        assertThat(html).contains(
                "href=\"/orders/8\"", "href=\"/orders/7\"",
                "Мяч (2 шт.) 5000 руб.", "Ракетка (1 шт.) 7800 руб.",
                "Сумма: 12800 руб.", "Сумма: 12900 руб.");
    }

    @Test
    void getOrder_shouldRenderOrderWithoutCongratulation_byDefault() {
        when(orderService.getOrder(ALICE.getId(), 7)).thenReturn(Mono.just(ORDER));

        String html = getHtml("/orders/7");

        assertThat(html).contains("Заказ №7", "2 шт.", "Сумма: 5000 руб.", "Сумма: 12800 руб.")
                .doesNotContain("Поздравляем");
    }

    @Test
    void getOrder_shouldShowCongratulation_forNewOrder() {
        when(orderService.getOrder(ALICE.getId(), 7)).thenReturn(Mono.just(ORDER));

        assertThat(getHtml("/orders/7?newOrder=true")).contains("Поздравляем");
    }

    @Test
    void getOrder_shouldReturnNotFound_whenOrderMissing() {
        when(orderService.getOrder(ALICE.getId(), 99)).thenReturn(Mono.error(new OrderNotFoundException(99)));

        webTestClient.get().uri("/orders/99")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getOrder_shouldReturnNotFound_whenOrderBelongsToAnotherUser() {
        when(orderService.getOrder(BOB.getId(), 7)).thenReturn(Mono.error(new OrderNotFoundException(7)));

        clientOf(BOB).get().uri("/orders/7")
                .exchange()
                .expectStatus().isNotFound();

        verify(orderService, never()).getOrder(ALICE.getId(), 7);
    }

    @Test
    void orders_shouldReturnUnauthorized_forAnonymous() {
        anonymousClient.get().uri("/orders")
                .exchange()
                .expectStatus().isUnauthorized();
        anonymousClient.get().uri("/orders/7")
                .exchange()
                .expectStatus().isUnauthorized();

        verify(orderService, never()).getOrders(anyLong());
        verify(orderService, never()).getOrder(anyLong(), anyLong());
    }
}
