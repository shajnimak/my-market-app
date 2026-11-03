package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.exception.ImageNotFoundException;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemImage;
import ru.yandex.practicum.mymarket.model.ItemSort;
import ru.yandex.practicum.mymarket.repository.ItemImageRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;
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

    @InjectMocks
    private ItemService itemService;

    private final Item ball = item(1, "Мяч", 2500);
    private final Item racket = item(2, "Ракетка", 7800);

    @Test
    void findItems_withoutSearch_shouldReturnRequestedPage() {
        Pageable pageable = PageRequest.of(1, 2, ItemSort.PRICE.toSort());
        Page<Item> expected = new PageImpl<>(List.of(ball, racket), pageable, 5);
        when(itemRepository.findAll(pageable)).thenReturn(expected);

        assertThat(itemService.findItems("  ", ItemSort.PRICE, 2, 2)).isSameAs(expected);
        verify(itemRepository, never()).search(anyString(), any());
    }

    @Test
    void findItems_withSearch_shouldSearchByStrippedString() {
        Pageable pageable = PageRequest.of(0, 5, ItemSort.NO.toSort());
        when(itemRepository.search("мяч", pageable)).thenReturn(new PageImpl<>(List.of(ball), pageable, 1));

        Page<Item> page = itemService.findItems(" мяч ", ItemSort.NO, 1, 5);

        assertThat(page.getContent()).containsExactly(ball);
    }

    @Test
    void findItems_shouldNormalizeInvalidPaging() {
        Pageable pageable = PageRequest.of(0, ItemService.MAX_PAGE_SIZE, ItemSort.NO.toSort());
        when(itemRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<Item> page = itemService.findItems("", ItemSort.NO, 0, 1000);

        assertThat(page.getPageable()).isEqualTo(pageable);
    }

    @Test
    void getItem_shouldReturnItem() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(ball));

        assertThat(itemService.getItem(1)).isSameAs(ball);
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
