package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemSort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        entityManager.persist(new Item("Футбольный мяч", "Для игры на траве", 2500));
        entityManager.persist(new Item("Ракетка", "Теннисная, для игры с МЯЧОМ", 7800));
        entityManager.persist(new Item("Лыжи", "Беговые", 12900));
        entityManager.persist(new Item("Баскетбольный мяч", "Для зала", 3200));
        entityManager.flush();
    }

    @Test
    void search_shouldMatchTitleOrDescriptionIgnoringCase() {
        Page<Item> page = itemRepository.search("мяч", PageRequest.of(0, 10, ItemSort.ALPHA.toSort()));

        assertThat(page.getContent())
                .extracting(Item::getTitle)
                .containsExactly("Баскетбольный мяч", "Ракетка", "Футбольный мяч");
    }

    @Test
    void search_shouldReturnEmptyPage_whenNothingMatches() {
        Page<Item> page = itemRepository.search("велосипед", PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void findAll_shouldSortByPriceAndSplitIntoPages() {
        Page<Item> firstPage = itemRepository.findAll(PageRequest.of(0, 3, ItemSort.PRICE.toSort()));
        Page<Item> secondPage = itemRepository.findAll(PageRequest.of(1, 3, ItemSort.PRICE.toSort()));

        assertThat(firstPage.getContent()).extracting(Item::getPrice).containsExactly(2500L, 3200L, 7800L);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.hasPrevious()).isFalse();
        assertThat(secondPage.getContent()).extracting(Item::getPrice).containsExactly(12900L);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.hasPrevious()).isTrue();
    }

    @Test
    void findAll_withoutSortType_shouldKeepInsertionOrder() {
        Page<Item> page = itemRepository.findAll(PageRequest.of(0, 10, ItemSort.NO.toSort()));

        assertThat(page.getContent())
                .extracting(Item::getTitle)
                .containsExactly("Футбольный мяч", "Ракетка", "Лыжи", "Баскетбольный мяч");
    }
}
