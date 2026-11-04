package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.cache.ReactiveRedisCache;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.ItemCard;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.exception.ImageNotFoundException;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemImage;
import ru.yandex.practicum.mymarket.model.ItemSort;
import ru.yandex.practicum.mymarket.repository.ItemImageRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ru.yandex.practicum.mymarket.TestData.card;
import static ru.yandex.practicum.mymarket.TestData.item;
import static ru.yandex.practicum.mymarket.TestData.missingCache;
import static ru.yandex.practicum.mymarket.TestData.summary;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemImageRepository itemImageRepository;

    @Mock
    private TransactionalOperator transactionalOperator;

    private ReactiveRedisCache<CatalogPage> catalogPageCache;
    private ReactiveRedisCache<ItemCard> itemCardCache;
    private ReactiveRedisCache<ImageData> itemImageCache;

    private ItemService itemService;

    private final Item ball = item(1, "Мяч", 2500);
    private final Item racket = item(2, "Ракетка", 7800);

    @BeforeEach
    void setUp() {
        catalogPageCache = missingCache();
        itemCardCache = missingCache();
        itemImageCache = missingCache();
        itemService = new ItemService(itemRepository, itemImageRepository,
                catalogPageCache, itemCardCache, itemImageCache, transactionalOperator);
    }

    @Test
    void findItems_withoutSearch_shouldLoadRequestedPageWithTotal_onCacheMiss() {
        Pageable pageable = PageRequest.of(1, 2, ItemSort.PRICE.toSort());
        when(itemRepository.findAllBy(pageable)).thenReturn(Flux.just(ball, racket));
        when(itemRepository.count()).thenReturn(Mono.just(5L));

        StepVerifier.create(itemService.findItems("  ", ItemSort.PRICE, 2, 2))
                .assertNext(page -> {
                    assertThat(page).isEqualTo(new CatalogPage(
                            List.of(summary(1, "Мяч", 2500), summary(2, "Ракетка", 7800)), 2, 2, 5));
                    assertThat(page.hasPrevious()).isTrue();
                    assertThat(page.hasNext()).isTrue();
                })
                .verifyComplete();

        verify(catalogPageCache).get(eq("PRICE:2:2:"), any());
        verify(itemRepository, never()).search(anyString(), any());
    }

    @Test
    void findItems_withSearch_shouldSearchByStrippedString_andCacheByLowercaseKey() {
        Pageable pageable = PageRequest.of(0, 5, ItemSort.NO.toSort());
        when(itemRepository.search("Мяч", pageable)).thenReturn(Flux.just(ball));
        when(itemRepository.countSearch("Мяч")).thenReturn(Mono.just(1L));

        StepVerifier.create(itemService.findItems(" Мяч ", ItemSort.NO, 1, 5))
                .assertNext(page -> {
                    assertThat(page.items()).containsExactly(summary(1, "Мяч", 2500));
                    assertThat(page.hasNext()).isFalse();
                })
                .verifyComplete();

        verify(catalogPageCache).get(eq("NO:1:5:мяч"), any());
        verify(itemRepository, never()).findAllBy(any());
    }

    @Test
    void findItems_shouldNormalizeInvalidPaging() {
        Pageable pageable = PageRequest.of(0, ItemService.MAX_PAGE_SIZE, ItemSort.ALPHA.toSort());
        when(itemRepository.findAllBy(pageable)).thenReturn(Flux.empty());
        when(itemRepository.count()).thenReturn(Mono.just(0L));

        StepVerifier.create(itemService.findItems(null, ItemSort.ALPHA, -3, 1000))
                .expectNext(new CatalogPage(List.of(), 1, ItemService.MAX_PAGE_SIZE, 0))
                .verifyComplete();

        verify(catalogPageCache).get(eq("ALPHA:1:100:"), any());
    }

    @Test
    void findItems_shouldNotQueryDatabase_onCacheHit() {
        CatalogPage cached = new CatalogPage(List.of(summary(1, "Мяч", 2500)), 1, 5, 1);
        when(catalogPageCache.get(eq("NO:1:5:"), any())).thenReturn(Mono.just(cached));

        StepVerifier.create(itemService.findItems("", ItemSort.NO, 1, 5))
                .expectNext(cached)
                .verifyComplete();

        verifyNoInteractions(itemRepository);
    }

    @Test
    void getItem_shouldLoadCardFromDatabase_onCacheMiss() {
        when(itemRepository.findById(2L)).thenReturn(Mono.just(racket));

        StepVerifier.create(itemService.getItem(2))
                .expectNext(new ItemCard(2, "Ракетка", "Описание Ракетка", "images/2", 7800))
                .verifyComplete();

        verify(itemCardCache).get(eq("2"), any());
    }

    @Test
    void getItem_shouldNotQueryDatabase_onCacheHit() {
        when(itemCardCache.get(eq("2"), any())).thenReturn(Mono.just(card(2, "Ракетка", 7800)));

        StepVerifier.create(itemService.getItem(2))
                .expectNext(card(2, "Ракетка", 7800))
                .verifyComplete();

        verifyNoInteractions(itemRepository);
    }

    @Test
    void getItem_shouldFail_whenItemMissing() {
        when(itemRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(itemService.getItem(99))
                .expectError(ItemNotFoundException.class)
                .verify();
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

        verify(itemImageCache).get(eq("1"), any());
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
    @SuppressWarnings("unchecked")
    void createItem_shouldSaveItemAndImageInTransaction_thenEvictCatalogPages() {
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

        InOrder inOrder = inOrder(transactionalOperator, catalogPageCache);
        inOrder.verify(transactionalOperator).transactional(any(Mono.class));
        inOrder.verify(catalogPageCache).evictAll();
        verifyNoInteractions(itemCardCache, itemImageCache);
    }
}
