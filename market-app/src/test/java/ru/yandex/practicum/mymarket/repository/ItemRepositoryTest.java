package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemSort;

import java.util.List;

class ItemRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        itemRepository.saveAll(List.of(
                new Item("Футбольный мяч", "Для игры на траве", 2500),
                new Item("Ракетка", "Теннисная, для игры с МЯЧОМ", 7800),
                new Item("Лыжи", "Беговые", 12900),
                new Item("Баскетбольный мяч", "Для зала", 3200)
        )).blockLast();
    }

    @Test
    void search_shouldMatchTitleOrDescriptionIgnoringCase() {
        StepVerifier.create(itemRepository.search("мяч", PageRequest.of(0, 10, ItemSort.ALPHA.toSort()))
                        .map(Item::getTitle))
                .expectNext("Баскетбольный мяч", "Ракетка", "Футбольный мяч")
                .verifyComplete();
    }

    @Test
    void countSearch_shouldCountAllMatchesRegardlessOfPage() {
        StepVerifier.create(itemRepository.countSearch("МЯЧ"))
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    void search_shouldReturnNothing_whenNothingMatches() {
        StepVerifier.create(itemRepository.search("велосипед", PageRequest.of(0, 10)))
                .verifyComplete();
        StepVerifier.create(itemRepository.countSearch("велосипед"))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void findAllBy_shouldSortByPriceAndSplitIntoPages() {
        StepVerifier.create(itemRepository.findAllBy(PageRequest.of(0, 3, ItemSort.PRICE.toSort())).map(Item::getPrice))
                .expectNext(2500L, 3200L, 7800L)
                .verifyComplete();
        StepVerifier.create(itemRepository.findAllBy(PageRequest.of(1, 3, ItemSort.PRICE.toSort())).map(Item::getPrice))
                .expectNext(12900L)
                .verifyComplete();
    }

    @Test
    void findAllBy_withoutSortType_shouldKeepInsertionOrder() {
        StepVerifier.create(itemRepository.findAllBy(PageRequest.of(0, 10, ItemSort.NO.toSort())).map(Item::getTitle))
                .expectNext("Футбольный мяч", "Ракетка", "Лыжи", "Баскетбольный мяч")
                .verifyComplete();
    }
}
