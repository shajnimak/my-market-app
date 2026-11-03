package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.exception.OrderNotFoundException;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.cartLine;
import static ru.yandex.practicum.mymarket.TestData.item;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T10:15:00Z"), ZoneOffset.UTC);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    private OrderService orderService;

    private final Item ball = item(1, "Мяч", 2500);
    private final Item racket = item(2, "Ракетка", 7800);

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderItemRepository, CLOCK);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createOrder_shouldCopyCartLinesAndCalculateTotal() {
        when(orderRepository.save(any())).thenAnswer(invocation -> Mono.just(withId(invocation.getArgument(0), 7)));
        when(orderItemRepository.saveAll(anyList()))
                .thenAnswer(invocation -> Flux.fromIterable(invocation.<List<OrderItem>>getArgument(0)));

        StepVerifier.create(orderService.createOrder(List.of(cartLine(ball, 2), cartLine(racket, 1))))
                .expectNext(7L)
                .verifyComplete();

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotalSum()).isEqualTo(2 * 2500 + 7800);
        assertThat(orderCaptor.getValue().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 10, 15));

        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue())
                .extracting(OrderItem::getOrderId, OrderItem::getItemId, OrderItem::getTitle,
                        OrderItem::getPrice, OrderItem::getQuantity)
                .containsExactly(
                        tuple(7L, 1L, "Мяч", 2500L, 2),
                        tuple(7L, 2L, "Ракетка", 7800L, 1)
                );
    }

    @Test
    void getOrder_shouldMapOrderWithItemsToDto() {
        when(orderRepository.findById(7L)).thenReturn(Mono.just(order(7, 12800)));
        when(orderItemRepository.findByOrderIdOrderByIdAsc(7L)).thenReturn(Flux.just(
                new OrderItem(7L, cartLine(ball, 2)),
                new OrderItem(7L, cartLine(racket, 1))
        ));

        StepVerifier.create(orderService.getOrder(7))
                .expectNext(new OrderDto(7, List.of(
                        new OrderItemDto(1, "Мяч", 2500, 2),
                        new OrderItemDto(2, "Ракетка", 7800, 1)
                ), 12800))
                .verifyComplete();
    }

    @Test
    void getOrder_shouldFail_whenOrderMissing() {
        when(orderRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(orderService.getOrder(99))
                .expectError(OrderNotFoundException.class)
                .verify();

        verify(orderItemRepository, never()).findByOrderIdOrderByIdAsc(anyLong());
    }

    @Test
    void getOrders_shouldLoadItemsOfAllOrdersWithOneQuery() {
        when(orderRepository.findAllByOrderByIdDesc()).thenReturn(Flux.just(order(8, 2500), order(7, 7800)));
        when(orderItemRepository.findByOrderIdInOrderByIdAsc(List.of(8L, 7L))).thenReturn(Flux.just(
                new OrderItem(7L, cartLine(racket, 1)),
                new OrderItem(8L, cartLine(ball, 1))
        ));

        StepVerifier.create(orderService.getOrders())
                .expectNext(new OrderDto(8, List.of(new OrderItemDto(1, "Мяч", 2500, 1)), 2500))
                .expectNext(new OrderDto(7, List.of(new OrderItemDto(2, "Ракетка", 7800, 1)), 7800))
                .verifyComplete();
    }

    @Test
    void getOrders_shouldNotQueryItems_whenNoOrders() {
        when(orderRepository.findAllByOrderByIdDesc()).thenReturn(Flux.empty());

        StepVerifier.create(orderService.getOrders()).verifyComplete();

        verify(orderItemRepository, never()).findByOrderIdInOrderByIdAsc(anyCollection());
    }

    private static Order order(long id, long totalSum) {
        return withId(new Order(LocalDateTime.now(CLOCK), totalSum), id);
    }

    private static Order withId(Order order, long id) {
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
