package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        when(itemRepository.findAllBy(pageable)).thenReturn(Flux.just(ball, racket));
        when(itemRepository.count()).thenReturn(Mono.just(5L));
        when(cartService.getCounts(List.of(1L, 2L))).thenReturn(Mono.just(Map.of(2L, 3)));

        StepVerifier.create(itemService.findItems("  ", ItemSort.PRICE, 2, 2))
                .assertNext(page -> {
                    assertThat(page.items()).containsExactly(
                            new ItemDto(1, "Мяч", "Описание Мяч", "images/1", 2500, 0),
                            new ItemDto(2, "Ракетка", "Описание Ракетка", "images/2", 7800, 3)
                    );
                    assertThat(page.paging()).isEqualTo(new Paging(2, 2, true, true));
                })
                .verifyComplete();

        verify(itemRepository, never()).search(anyString(), any());
    }

    @Test
    void findItems_withSearch_shouldSearchByStrippedString() {
        Pageable pageable = PageRequest.of(0, 5, ItemSort.NO.toSort());
        when(itemRepository.search("мяч", pageable)).thenReturn(Flux.just(ball));
        when(itemRepository.countSearch("мяч")).thenReturn(Mono.just(1L));
        when(cartService.getCounts(List.of(1L))).thenReturn(Mono.just(Map.of()));

        StepVerifier.create(itemService.findItems(" мяч ", ItemSort.NO, 1, 5))
                .assertNext(page -> {
                    assertThat(page.items()).extracting(ItemDto::title).containsExactly("Мяч");
                    assertThat(page.paging()).isEqualTo(new Paging(5, 1, false, false));
                })
                .verifyComplete();

        verify(itemRepository, never()).findAllBy(any());
    }

    @Test
    void findItems_shouldNormalizeInvalidPaging() {
        Pageable pageable = PageRequest.of(0, ItemService.MAX_PAGE_SIZE, ItemSort.ALPHA.toSort());
        when(itemRepository.findAllBy(pageable)).thenReturn(Flux.empty());
        when(itemRepository.count()).thenReturn(Mono.just(0L));
        when(cartService.getCounts(List.of())).thenReturn(Mono.just(Map.of()));

        StepVerifier.create(itemService.findItems(null, ItemSort.ALPHA, -3, 1000))
                .expectNext(new ItemsPage(List.of(), new Paging(ItemService.MAX_PAGE_SIZE, 1, false, false)))
                .verifyComplete();
    }

    @Test
    void getItem_shouldReturnItemWithCartCount() {
        when(itemRepository.findById(2L)).thenReturn(Mono.just(racket));
        when(cartService.getCount(2)).thenReturn(Mono.just(4));

        StepVerifier.create(itemService.getItem(2))
                .expectNext(new ItemDto(2, "Ракетка", "Описание Ракетка", "images/2", 7800, 4))
                .verifyComplete();
    }

    @Test
    void getItem_shouldFail_whenItemMissing() {
        when(itemRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(itemService.getItem(99))
                .expectError(ItemNotFoundException.class)
                .verify();

        verify(cartService, never()).getCount(anyLong());
    }

    @Test
    void getImage_shouldReturnStoredImage() {
        when(itemImageRepository.findById(1L))
                .thenReturn(Mono.just(ItemImage.newImage(1L, "image/png", new byte[]{7})));

        StepVerifier.create(itemService.getImage(1))
                .assertNext(image -> {
                    assertThat(image.contentType()).isEqualTo("image/png");
                    assertThat(image.content()).containsExactly(7);
                })
                .verifyComplete();
    }

    @Test
    void getImage_shouldFail_whenImageMissing() {
        when(itemImageRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(itemService.getImage(1))
                .expectError(ImageNotFoundException.class)
                .verify();
    }

    @Test
    void isCatalogEmpty_shouldCheckItemCount() {
        when(itemRepository.count()).thenReturn(Mono.just(0L), Mono.just(3L));

        StepVerifier.create(itemService.isCatalogEmpty()).expectNext(true).verifyComplete();
        StepVerifier.create(itemService.isCatalogEmpty()).expectNext(false).verifyComplete();
    }

    @Test
    void createItem_shouldSaveItemAndItsImage() {
        when(itemRepository.save(any())).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 21L);
            return Mono.just(item);
        });
        when(itemImageRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(itemService.createItem(
                        new NewItem("Мяч", "Футбольный", 2500, new ImageData("image/png", new byte[]{1, 2}))))
                .expectNext(21L)
                .verifyComplete();

        ArgumentCaptor<ItemImage> captor = ArgumentCaptor.forClass(ItemImage.class);
        verify(itemImageRepository).save(captor.capture());
        ItemImage image = captor.getValue();
        assertThat(image.getItemId()).isEqualTo(21L);
        assertThat(image.isNew()).isTrue();
        assertThat(image.getContentType()).isEqualTo("image/png");
        assertThat(image.getContent()).containsExactly(1, 2);
    }
}
