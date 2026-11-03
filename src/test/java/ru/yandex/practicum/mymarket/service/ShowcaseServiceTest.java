package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemSort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                .thenReturn(new PageImpl<>(List.of(ball, racket), PageRequest.of(1, 2), 5));
        when(cartService.getCounts(List.of(1L, 2L))).thenReturn(Map.of(2L, 3));

        ItemsPage page = showcaseService.findItems("мяч", ItemSort.PRICE, 2, 2);

        assertThat(page.items()).containsExactly(
                new ItemDto(1, "Мяч", "Описание Мяч", "images/1", 2500, 0),
                new ItemDto(2, "Ракетка", "Описание Ракетка", "images/2", 7800, 3)
        );
        assertThat(page.paging()).isEqualTo(new Paging(2, 2, true, true));
    }

    @Test
    void findItems_shouldReturnEmptyPage_whenNothingFound() {
        when(itemService.findItems("велосипед", ItemSort.NO, 1, 5))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));
        when(cartService.getCounts(List.of())).thenReturn(Map.of());

        ItemsPage page = showcaseService.findItems("велосипед", ItemSort.NO, 1, 5);

        assertThat(page.items()).isEmpty();
        assertThat(page.paging()).isEqualTo(new Paging(5, 1, false, false));
    }

    @Test
    void getItem_shouldReturnItemWithCartCount() {
        when(itemService.getItem(1)).thenReturn(ball);
        when(cartService.getCount(1)).thenReturn(2);

        assertThat(showcaseService.getItem(1))
                .isEqualTo(new ItemDto(1, "Мяч", "Описание Мяч", "images/1", 2500, 2));
    }

    @Test
    void getItem_shouldNotAskCart_whenItemMissing() {
        when(itemService.getItem(99)).thenThrow(new ItemNotFoundException(99));

        assertThatThrownBy(() -> showcaseService.getItem(99)).isInstanceOf(ItemNotFoundException.class);
        verify(cartService, never()).getCount(anyLong());
    }
}
