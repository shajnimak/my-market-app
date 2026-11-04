package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.CartLine;
import ru.yandex.practicum.mymarket.model.Item;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.yandex.practicum.mymarket.TestData.cartItem;

class CartItemRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Item ball;
    private Item racket;
    private Item ski;

    @BeforeEach
    void setUp() {
        ball = itemRepository.save(new Item("Мяч", "Футбольный", 2500)).block();
        racket = itemRepository.save(new Item("Ракетка", "Теннисная", 7800)).block();
        ski = itemRepository.save(new Item("Лыжи", "Беговые", 12900)).block();
        cartItemRepository.save(cartItem(racket.getId(), 2))
                .then(cartItemRepository.save(cartItem(ball.getId(), 1)))
                .block();
    }

    @Test
    void findByItemId_shouldReturnCartItem_whenItemInCart() {
        StepVerifier.create(cartItemRepository.findByItemId(racket.getId()))
                .assertNext(cartItem -> assertThat(cartItem.getQuantity()).isEqualTo(2))
                .verifyComplete();
        StepVerifier.create(cartItemRepository.findByItemId(ski.getId()))
                .verifyComplete();
    }

    @Test
    void findByItemIdIn_shouldReturnOnlyItemsInCart() {
        StepVerifier.create(cartItemRepository.findByItemIdIn(List.of(ball.getId(), ski.getId())).map(CartItem::getItemId))
                .expectNext(ball.getId())
                .verifyComplete();
    }

    @Test
    void findCartLines_shouldJoinItemsInAddingOrder() {
        StepVerifier.create(cartItemRepository.findCartLines())
                .expectNext(new CartLine(racket.getId(), "Ракетка", "Теннисная", 7800, 2))
                .expectNext(new CartLine(ball.getId(), "Мяч", "Футбольный", 2500, 1))
                .verifyComplete();
    }

    @Test
    void save_shouldRejectSecondCartItemForSameItem() {
        StepVerifier.create(cartItemRepository.save(new CartItem(ball.getId())))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }
}
