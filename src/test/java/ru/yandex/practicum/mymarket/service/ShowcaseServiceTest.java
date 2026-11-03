package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemSort;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.item;

@ExtendWith(MockitoExtension.class)
class ShowcaseServiceTest {

    @Mock
    private ItemService itemService;

    @Mock
    private CartService cartService;

    @InjectMocks
    private ShowcaseService showcaseService;

    private final Item ball = item(1, "Мяч", 2500);
    private final Item racket = item(2, "Ракетка", 7800);

    @Test
    void findItems_shouldCombineCatalogPageWithCartCounts() {
        when(itemService.findItems("мяч", ItemSort.PRICE, 2, 2))
                .thenReturn(Mono.just(new PageImpl<>(List.of(ball, racket), PageRequest.of(1, 2), 5)));
        when(cartService.getCounts(List.of(1L, 2L))).thenReturn(Mono.just(Map.of(2L, 3)));

        StepVerifier.create(showcaseService.findItems("мяч", ItemSort.PRICE, 2, 2))
                .expectNext(new ItemsPage(List.of(
                        new ItemDto(1, "Мяч", "Описание Мяч", "images/1", 2500, 0),
                        new ItemDto(2, "Ракетка", "Описание Ракетка", "images/2", 7800, 3)
                ), new Paging(2, 2, true, true)))
                .verifyComplete();
    }

    @Test
    void findItems_shouldReturnEmptyPage_whenNothingFound() {
        when(itemService.findItems("велосипед", ItemSort.NO, 1, 5))
                .thenReturn(Mono.just(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0)));
        when(cartService.getCounts(List.of())).thenReturn(Mono.just(Map.of()));

        StepVerifier.create(showcaseService.findItems("велосипед", ItemSort.NO, 1, 5))
                .expectNext(new ItemsPage(List.of(), new Paging(5, 1, false, false)))
                .verifyComplete();
    }

    @Test
    void getItem_shouldReturnItemWithCartCount() {
        when(itemService.getItem(1)).thenReturn(Mono.just(ball));
        when(cartService.getCount(1)).thenReturn(Mono.just(2));

        StepVerifier.create(showcaseService.getItem(1))
                .expectNext(new ItemDto(1, "Мяч", "Описание Мяч", "images/1", 2500, 2))
                .verifyComplete();
    }

    @Test
    void getItem_shouldNotAskCart_whenItemMissing() {
        when(itemService.getItem(99)).thenReturn(Mono.error(new ItemNotFoundException(99)));

        StepVerifier.create(showcaseService.getItem(99))
                .expectError(ItemNotFoundException.class)
                .verify();

        verify(cartService, never()).getCount(anyLong());
    }
}
