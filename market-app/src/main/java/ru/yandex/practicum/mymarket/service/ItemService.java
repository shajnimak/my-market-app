package ru.yandex.practicum.mymarket.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.cache.ReactiveRedisCache;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.ItemCard;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.exception.ImageNotFoundException;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.mapper.ItemMapper;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemImage;
import ru.yandex.practicum.mymarket.model.ItemSort;
import ru.yandex.practicum.mymarket.repository.ItemImageRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.Locale;

/**
 * Каталог товаров: поиск, карточка, картинки и добавление товаров. Не зависит от корзины.
 * Страницы поиска, карточки и картинки читаются из кеша Redis, а при промахе загружаются из базы и кладутся в кеш.
 * Чтение из кеша не открывает транзакцию базы данных: соединение с базой нужно только при промахе.
 */
@Service
public class ItemService {

    static final int MAX_PAGE_SIZE = 100;

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final ReactiveRedisCache<CatalogPage> catalogPageCache;
    private final ReactiveRedisCache<ItemCard> itemCardCache;
    private final ReactiveRedisCache<ImageData> itemImageCache;
    private final TransactionalOperator transactionalOperator;

    public ItemService(ItemRepository itemRepository,
                       ItemImageRepository itemImageRepository,
                       ReactiveRedisCache<CatalogPage> catalogPageCache,
                       ReactiveRedisCache<ItemCard> itemCardCache,
                       ReactiveRedisCache<ImageData> itemImageCache,
                       TransactionalOperator transactionalOperator) {
        this.itemRepository = itemRepository;
        this.itemImageRepository = itemImageRepository;
        this.catalogPageCache = catalogPageCache;
        this.itemCardCache = itemCardCache;
        this.itemImageCache = itemImageCache;
        this.transactionalOperator = transactionalOperator;
    }

    /**
     * Страница товаров, у которых название или описание содержит строку поиска (без учёта регистра).
     * Кешируется по ключу из сортировки, номера и размера страницы и строки поиска в нижнем регистре.
     */
    public Mono<CatalogPage> findItems(String search, ItemSort sort, int pageNumber, int pageSize) {
        int number = Math.max(pageNumber, 1);
        int size = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        String normalizedSearch = search == null ? "" : search.strip();
        String key = sort + ":" + number + ":" + size + ":" + normalizedSearch.toLowerCase(Locale.ROOT);
        return catalogPageCache.get(key, () -> loadPage(normalizedSearch, sort, number, size));
    }

    public Mono<ItemCard> getItem(long id) {
        return itemCardCache.get(String.valueOf(id), () -> itemRepository.findById(id)
                .map(ItemMapper::toCard)
                .switchIfEmpty(Mono.error(() -> new ItemNotFoundException(id))));
    }

    public Mono<ImageData> getImage(long itemId) {
        return itemImageCache.get(String.valueOf(itemId), () -> itemImageRepository.findById(itemId)
                .map(image -> new ImageData(image.getContentType(), image.getContent()))
                .switchIfEmpty(Mono.error(() -> new ImageNotFoundException(itemId))));
    }

    public Mono<Boolean> isCatalogEmpty() {
        return itemRepository.count().map(count -> count == 0);
    }

    /**
     * Сохраняет товар с картинкой в одной транзакции. Новый товар может попасть на любую страницу поиска,
     * поэтому после фиксации транзакции закешированные страницы сбрасываются.
     * Карточек и картинки нового товара в кеше ещё нет.
     */
    public Mono<Long> createItem(NewItem newItem) {
        ImageData image = newItem.image();
        return itemRepository.save(new Item(newItem.title(), newItem.description(), newItem.price()))
                .flatMap(item -> itemImageRepository
                        .save(ItemImage.newImage(item.getId(), image.contentType(), image.content()))
                        .thenReturn(item.getId()))
                .as(transactionalOperator::transactional)
                .flatMap(itemId -> catalogPageCache.evictAll().thenReturn(itemId));
    }

    private Mono<CatalogPage> loadPage(String search, ItemSort sort, int number, int size) {
        Pageable pageable = PageRequest.of(number - 1, size, sort.toSort());
        boolean filtered = !search.isEmpty();
        Flux<Item> items = filtered ? itemRepository.search(search, pageable) : itemRepository.findAllBy(pageable);
        Mono<Long> total = filtered ? itemRepository.countSearch(search) : itemRepository.count();
        return items.map(ItemMapper::toSummary)
                .collectList()
                .zipWith(total, (pageItems, count) -> new CatalogPage(pageItems, number, size, count));
    }
}
