package ru.yandex.practicum.mymarket.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.exception.ImageNotFoundException;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemImage;
import ru.yandex.practicum.mymarket.model.ItemSort;
import ru.yandex.practicum.mymarket.repository.ItemImageRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

/**
 * Каталог товаров: поиск, карточка, картинки и добавление товаров. Не зависит от корзины.
 */
@Service
@Transactional(readOnly = true)
public class ItemService {

    static final int MAX_PAGE_SIZE = 100;

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;

    public ItemService(ItemRepository itemRepository, ItemImageRepository itemImageRepository) {
        this.itemRepository = itemRepository;
        this.itemImageRepository = itemImageRepository;
    }

    public Mono<Page<Item>> findItems(String search, ItemSort sort, int pageNumber, int pageSize) {
        int number = Math.max(pageNumber, 1);
        int size = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(number - 1, size, sort.toSort());

        boolean filtered = StringUtils.hasText(search);
        Flux<Item> items = filtered ? itemRepository.search(search.strip(), pageable) : itemRepository.findAllBy(pageable);
        Mono<Long> total = filtered ? itemRepository.countSearch(search.strip()) : itemRepository.count();

        return items.collectList()
                .flatMap(pageItems -> total.map(count -> new PageImpl<>(pageItems, pageable, count)));
    }

    public Mono<Item> getItem(long id) {
        return itemRepository.findById(id)
                .switchIfEmpty(Mono.error(() -> new ItemNotFoundException(id)));
    }

    public Mono<ImageData> getImage(long itemId) {
        return itemImageRepository.findById(itemId)
                .map(image -> new ImageData(image.getContentType(), image.getContent()))
                .switchIfEmpty(Mono.error(() -> new ImageNotFoundException(itemId)));
    }

    public Mono<Boolean> isCatalogEmpty() {
        return itemRepository.count().map(count -> count == 0);
    }

    @Transactional
    public Mono<Long> createItem(NewItem newItem) {
        ImageData image = newItem.image();
        return itemRepository.save(new Item(newItem.title(), newItem.description(), newItem.price()))
                .flatMap(item -> itemImageRepository
                        .save(ItemImage.newImage(item.getId(), image.contentType(), image.content()))
                        .thenReturn(item.getId()));
    }
}
