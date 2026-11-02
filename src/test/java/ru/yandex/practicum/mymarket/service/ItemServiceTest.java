package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.exception.ImageNotFoundException;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemImage;
import ru.yandex.practicum.mymarket.model.ItemSort;
import ru.yandex.practicum.mymarket.repository.ItemImageRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.item;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemImageRepository itemImageRepository;

    @Mock
    private CartService cartService;

    @InjectMocks
    private ItemService itemService;

    private final Item ball = item(1, "Мяч", 2500);
    private final Item racket = item(2, "Ракетка", 7800);

    @Test
    void findItems_withoutSearch_shouldReturnPageWithCartCounts() {
        Pageable pageable = PageRequest.of(1, 2, ItemSort.PRICE.toSort());
        when(itemRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(ball, racket), pageable, 5));
        when(cartService.getCounts(List.of(1L, 2L))).thenReturn(Map.of(2L, 3));

        ItemsPage page = itemService.findItems("  ", ItemSort.PRICE, 2, 2);

        assertThat(page.items()).containsExactly(
                new ItemDto(1, "Мяч", "Описание Мяч", "images/1", 2500, 0),
                new ItemDto(2, "Ракетка", "Описание Ракетка", "images/2", 7800, 3)
        );
        assertThat(page.paging()).isEqualTo(new Paging(2, 2, true, true));
        verify(itemRepository, never()).search(anyString(), any());
    }

    @Test
    void findItems_withSearch_shouldSearchByStrippedString() {
        Pageable pageable = PageRequest.of(0, 5, ItemSort.NO.toSort());
        when(itemRepository.search("мяч", pageable)).thenReturn(new PageImpl<>(List.of(ball), pageable, 1));
        when(cartService.getCounts(List.of(1L))).thenReturn(Map.of());

        ItemsPage page = itemService.findItems(" мяч ", ItemSort.NO, 1, 5);

        assertThat(page.items()).extracting(ItemDto::title).containsExactly("Мяч");
        assertThat(page.paging()).isEqualTo(new Paging(5, 1, false, false));
    }

    @Test
    void findItems_shouldNormalizeInvalidPaging() {
        Pageable pageable = PageRequest.of(0, ItemService.MAX_PAGE_SIZE, ItemSort.NO.toSort());
        when(itemRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(cartService.getCounts(List.of())).thenReturn(Map.of());

        ItemsPage page = itemService.findItems("", ItemSort.NO, 0, 1000);

        assertThat(page.paging()).isEqualTo(new Paging(ItemService.MAX_PAGE_SIZE, 1, false, false));
    }

    @Test
    void getItem_shouldReturnItemWithCartCount() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(ball));
        when(cartService.getCount(1L)).thenReturn(2);

        assertThat(itemService.getItem(1))
                .isEqualTo(new ItemDto(1, "Мяч", "Описание Мяч", "images/1", 2500, 2));
    }

    @Test
    void getItem_shouldThrow_whenItemMissing() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getItem(99)).isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void getImage_shouldReturnStoredImage() {
        byte[] content = {1, 2, 3};
        when(itemImageRepository.findById(1L)).thenReturn(Optional.of(new ItemImage(1L, "image/png", content)));

        ImageData image = itemService.getImage(1);

        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.content()).isSameAs(content);
    }

    @Test
    void getImage_shouldThrow_whenImageMissing() {
        when(itemImageRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getImage(1)).isInstanceOf(ImageNotFoundException.class);
    }

    @Test
    void createItem_shouldSaveItemAndItsImage() {
        when(itemRepository.save(any(Item.class))).thenReturn(item(7, "Мяч", 100));
        byte[] content = {9};

        long id = itemService.createItem(new NewItem("Мяч", "Описание", 100, new ImageData("image/svg+xml", content)));

        assertThat(id).isEqualTo(7);
        ArgumentCaptor<ItemImage> captor = ArgumentCaptor.forClass(ItemImage.class);
        verify(itemImageRepository).save(captor.capture());
        assertThat(captor.getValue().getItemId()).isEqualTo(7);
        assertThat(captor.getValue().getContentType()).isEqualTo("image/svg+xml");
        assertThat(captor.getValue().getContent()).isSameAs(content);
    }
}
