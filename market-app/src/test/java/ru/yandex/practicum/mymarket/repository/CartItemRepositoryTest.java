package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.yandex.practicum.mymarket.TestData.cartItem;

class CartItemRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    private long aliceId;
    private long bobId;
    private Item ball;
    private Item racket;
    private Item ski;

    @BeforeEach
    void setUp() {
        aliceId = userRepository.save(new User("alice", "{noop}password")).block().getId();
        bobId = userRepository.save(new User("bob", "{noop}password")).block().getId();
        ball = itemRepository.save(new Item("Мяч", "Футбольный", 2500)).block();
        racket = itemRepository.save(new Item("Ракетка", "Теннисная", 7800)).block();
        ski = itemRepository.save(new Item("Лыжи", "Беговые", 12900)).block();
        cartItemRepository.save(cartItem(aliceId, racket.getId(), 2))
                .then(cartItemRepository.save(cartItem(aliceId, ball.getId(), 1)))
                .then(cartItemRepository.save(cartItem(bobId, ski.getId(), 5)))
                .block();
    }

    @Test
    void findByUserIdAndItemId_shouldReturnCartItem_onlyFromCartOfThatUser() {
        StepVerifier.create(cartItemRepository.findByUserIdAndItemId(aliceId, racket.getId()))
                .assertNext(cartItem -> assertThat(cartItem.getQuantity()).isEqualTo(2))
                .verifyComplete();
        StepVerifier.create(cartItemRepository.findByUserIdAndItemId(aliceId, ski.getId()))
                .verifyComplete();
        StepVerifier.create(cartItemRepository.findByUserIdAndItemId(bobId, racket.getId()))
                .verifyComplete();
    }

    @Test
    void findByUserIdAndItemIdIn_shouldReturnOnlyItemsInCartOfThatUser() {
        StepVerifier.create(cartItemRepository.findByUserIdAndItemIdIn(aliceId, List.of(ball.getId(), ski.getId()))
                        .map(CartItem::getItemId))
                .expectNext(ball.getId())
                .verifyComplete();
    }

    @Test
    void findByUserIdOrderByIdAsc_shouldReturnCartItemsOfThatUserInAddingOrder() {
        StepVerifier.create(cartItemRepository.findByUserIdOrderByIdAsc(aliceId))
                .assertNext(cartItem -> {
                    assertThat(cartItem.getItemId()).isEqualTo(racket.getId());
                    assertThat(cartItem.getQuantity()).isEqualTo(2);
                })
                .assertNext(cartItem -> {
                    assertThat(cartItem.getItemId()).isEqualTo(ball.getId());
                    assertThat(cartItem.getQuantity()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void save_shouldRejectSecondCartItemForSameItemInSameCart() {
        StepVerifier.create(cartItemRepository.save(new CartItem(aliceId, ball.getId())))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void save_shouldAllowSameItemInCartsOfDifferentUsers() {
        StepVerifier.create(cartItemRepository.save(new CartItem(bobId, ball.getId())).map(CartItem::getUserId))
                .expectNext(bobId)
                .verifyComplete();
    }

    @Test
    void deleteByUserId_shouldClearOnlyCartOfThatUser() {
        StepVerifier.create(cartItemRepository.deleteByUserId(aliceId)).verifyComplete();

        StepVerifier.create(cartItemRepository.countByUserId(aliceId)).expectNext(0L).verifyComplete();
        StepVerifier.create(cartItemRepository.countByUserId(bobId)).expectNext(1L).verifyComplete();
    }
}
