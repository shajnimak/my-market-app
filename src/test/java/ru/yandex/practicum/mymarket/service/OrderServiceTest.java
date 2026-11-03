package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.exception.OrderNotFoundException;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.cartItem;
import static ru.yandex.practicum.mymarket.TestData.item;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:15:30Z");

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;

    private final Item ball = item(1, "Мяч", 2500);
    private final Item racket = item(2, "Ракетка", 7800);

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createOrder_shouldCopyCartItemsAndCalculateTotal() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 10L);
            return order;
        });

        long orderId = orderService.createOrder(List.of(cartItem(ball, 2), cartItem(racket, 1)));

        assertThat(orderId).isEqualTo(10);
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order order = captor.getValue();
        assertThat(order.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 10, 15, 30));
        assertThat(order.getTotalSum()).isEqualTo(2 * 2500 + 7800);
        assertThat(order.getItems())
                .extracting(OrderItem::getTitle, OrderItem::getPrice, OrderItem::getQuantity)
                .containsExactly(
                        tuple("Мяч", 2500L, 2),
                        tuple("Ракетка", 7800L, 1)
                );
    }

    @Test
    void getOrder_shouldMapOrderToDto() {
        Order order = new Order(LocalDateTime.now());
        order.addItem(new OrderItem(ball, 3));
        ReflectionTestUtils.setField(order, "id", 5L);
        when(orderRepository.findWithItemsById(5L)).thenReturn(Optional.of(order));

        OrderDto dto = orderService.getOrder(5);

        assertThat(dto).isEqualTo(new OrderDto(5, List.of(new OrderItemDto(1, "Мяч", 2500, 3)), 7500));
    }

    @Test
    void getOrder_shouldThrow_whenOrderMissing() {
        when(orderRepository.findWithItemsById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(5)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getOrders_shouldMapAllOrders() {
        Order order = new Order(LocalDateTime.now());
        order.addItem(new OrderItem(racket, 1));
        ReflectionTestUtils.setField(order, "id", 3L);
        when(orderRepository.findAllByOrderByIdDesc()).thenReturn(List.of(order));

        assertThat(orderService.getOrders())
                .containsExactly(new OrderDto(3, List.of(new OrderItemDto(2, "Ракетка", 7800, 1)), 7800));
    }
}
