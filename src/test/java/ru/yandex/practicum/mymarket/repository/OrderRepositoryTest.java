package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    private Item ball;
    private Item racket;

    @BeforeEach
    void setUp() {
        ball = entityManager.persist(new Item("Мяч", "Футбольный", 2500));
        racket = entityManager.persist(new Item("Ракетка", "Теннисная", 7800));
    }

    @Test
    void save_shouldCascadeOrderItems() {
        Order order = new Order(LocalDateTime.of(2026, 11, 2, 12, 0));
        order.addItem(new OrderItem(ball, 2));
        order.addItem(new OrderItem(racket, 1));

        Long orderId = orderRepository.saveAndFlush(order).getId();
        entityManager.clear();

        Order saved = orderRepository.findWithItemsById(orderId).orElseThrow();
        assertThat(saved.getTotalSum()).isEqualTo(2 * 2500 + 7800);
        assertThat(saved.getItems())
                .extracting(OrderItem::getTitle, OrderItem::getPrice, OrderItem::getQuantity)
                .containsExactly(
                        tuple("Мяч", 2500L, 2),
                        tuple("Ракетка", 7800L, 1)
                );
    }

    @Test
    void findAllByOrderByIdDesc_shouldReturnNewestOrdersFirst() {
        Order first = new Order(LocalDateTime.now());
        first.addItem(new OrderItem(ball, 1));
        Order second = new Order(LocalDateTime.now());
        second.addItem(new OrderItem(racket, 3));
        orderRepository.save(first);
        orderRepository.save(second);
        entityManager.flush();
        entityManager.clear();

        List<Order> orders = orderRepository.findAllByOrderByIdDesc();

        assertThat(orders).extracting(Order::getId).containsExactly(second.getId(), first.getId());
        assertThat(orders.getFirst().getItems()).hasSize(1);
    }

    @Test
    void findWithItemsById_shouldReturnEmpty_whenOrderMissing() {
        assertThat(orderRepository.findWithItemsById(-1L)).isEmpty();
    }
}
