package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CartItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Item ball;
    private Item racket;
    private Item ski;

    @BeforeEach
    void setUp() {
        ball = entityManager.persist(new Item("Мяч", "Футбольный", 2500));
        racket = entityManager.persist(new Item("Ракетка", "Теннисная", 7800));
        ski = entityManager.persist(new Item("Лыжи", "Беговые", 12900));
        entityManager.persist(new CartItem(racket));
        entityManager.persist(new CartItem(ball));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findByItemId_shouldReturnCartItem_whenItemInCart() {
        assertThat(cartItemRepository.findByItemId(ball.getId()))
                .hasValueSatisfying(cartItem -> assertThat(cartItem.getQuantity()).isEqualTo(1));
        assertThat(cartItemRepository.findByItemId(ski.getId())).isEmpty();
    }

    @Test
    void findByItemIdIn_shouldReturnOnlyItemsInCart() {
        List<CartItem> cartItems = cartItemRepository.findByItemIdIn(List.of(ball.getId(), ski.getId()));

        assertThat(cartItems)
                .extracting(cartItem -> cartItem.getItem().getId())
                .containsExactly(ball.getId());
    }

    @Test
    void findAllByOrderByIdAsc_shouldReturnCartItemsInAddingOrderWithItems() {
        List<CartItem> cartItems = cartItemRepository.findAllByOrderByIdAsc();
        entityManager.clear();

        assertThat(cartItems)
                .extracting(cartItem -> cartItem.getItem().getTitle())
                .containsExactly("Ракетка", "Мяч");
    }

    @Test
    void save_shouldRejectSecondCartItemForSameItem() {
        Item item = entityManager.find(Item.class, ball.getId());

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(item)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
