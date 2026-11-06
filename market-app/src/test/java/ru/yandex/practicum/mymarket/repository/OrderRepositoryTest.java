package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.model.CartLine;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.model.User;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.yandex.practicum.mymarket.TestData.cartLine;

class OrderRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    private long aliceId;
    private long bobId;
    private Order firstOrder;
    private Order secondOrder;
    private Order bobOrder;

    @BeforeEach
    void setUp() {
        aliceId = userRepository.save(new User("alice", "{noop}password")).block().getId();
        bobId = userRepository.save(new User("bob", "{noop}password")).block().getId();
        Item ball = itemRepository.save(new Item("Мяч", "Футбольный", 2500)).block();
        Item racket = itemRepository.save(new Item("Ракетка", "Теннисная", 7800)).block();
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 12, 0);

        firstOrder = orderRepository.save(new Order(aliceId, now, 12800)).block();
        orderItemRepository.saveAll(List.of(
                new OrderItem(firstOrder.getId(), cartLine(ball, 2)),
                new OrderItem(firstOrder.getId(), cartLine(racket, 1))
        )).blockLast();
        secondOrder = orderRepository.save(new Order(aliceId, now.plusHours(1), 2500)).block();
        orderItemRepository.save(new OrderItem(secondOrder.getId(), cartLine(ball, 1))).block();
        bobOrder = orderRepository.save(new Order(bobId, now.plusHours(2), 7800)).block();
    }

    @Test
    void findByUserIdOrderByIdDesc_shouldReturnOrdersOfThatUserNewestFirst() {
        StepVerifier.create(orderRepository.findByUserIdOrderByIdDesc(aliceId).map(Order::getTotalSum))
                .expectNext(2500L, 12800L)
                .verifyComplete();
        StepVerifier.create(orderRepository.findByUserIdOrderByIdDesc(bobId).map(Order::getTotalSum))
                .expectNext(7800L)
                .verifyComplete();
    }

    @Test
    void findByIdAndUserId_shouldNotReturnOrderOfAnotherUser() {
        StepVerifier.create(orderRepository.findByIdAndUserId(firstOrder.getId(), aliceId).map(Order::getTotalSum))
                .expectNext(12800L)
                .verifyComplete();
        StepVerifier.create(orderRepository.findByIdAndUserId(firstOrder.getId(), bobId))
                .verifyComplete();
        StepVerifier.create(orderRepository.findByIdAndUserId(bobOrder.getId(), aliceId))
                .verifyComplete();
    }

    @Test
    void save_shouldStoreCreationTime() {
        StepVerifier.create(orderRepository.findById(firstOrder.getId()))
                .assertNext(order -> assertThat(order.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 12, 0)))
                .verifyComplete();
    }

    @Test
    void findByOrderIdOrderByIdAsc_shouldReturnCopiedTitlesAndPrices() {
        StepVerifier.create(orderItemRepository.findByOrderIdOrderByIdAsc(firstOrder.getId()))
                .assertNext(orderItem -> {
                    assertThat(orderItem.getTitle()).isEqualTo("Мяч");
                    assertThat(orderItem.getPrice()).isEqualTo(2500);
                    assertThat(orderItem.getQuantity()).isEqualTo(2);
                })
                .assertNext(orderItem -> assertThat(orderItem.getTitle()).isEqualTo("Ракетка"))
                .verifyComplete();
    }

    @Test
    void findByOrderIdInOrderByIdAsc_shouldReturnItemsOfAllRequestedOrders() {
        StepVerifier.create(orderItemRepository
                        .findByOrderIdInOrderByIdAsc(List.of(firstOrder.getId(), secondOrder.getId()))
                        .map(OrderItem::getOrderId))
                .expectNext(firstOrder.getId(), firstOrder.getId(), secondOrder.getId())
                .verifyComplete();
    }

    @Test
    void findById_shouldReturnEmpty_whenOrderMissing() {
        StepVerifier.create(orderRepository.findById(-1L))
                .verifyComplete();
    }
}
