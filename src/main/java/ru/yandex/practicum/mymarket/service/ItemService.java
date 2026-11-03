package ru.yandex.practicum.mymarket.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.exception.ImageNotFoundException;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.mapper.ItemMapper;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemImage;
import ru.yandex.practicum.mymarket.model.ItemSort;
import ru.yandex.practicum.mymarket.repository.ItemImageRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ItemService {

    static final int MAX_PAGE_SIZE = 100;

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final CartService cartService;

    public ItemService(ItemRepository itemRepository,
                       ItemImageRepository itemImageRepository,
                       CartService cartService) {
        this.itemRepository = itemRepository;
        this.itemImageRepository = itemImageRepository;
        this.cartService = cartService;
    }

    public Mono<ItemsPage> findItems(String search, ItemSort sort, int pageNumber, int pageSize) {
        int number = Math.max(pageNumber, 1);
        int size = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(number - 1, size, sort.toSort());

        boolean filtered = StringUtils.hasText(search);
        Flux<Item> items = filtered ? itemRepository.search(search.strip(), pageable) : itemRepository.findAllBy(pageable);
        Mono<Long> total = filtered ? itemRepository.countSearch(search.strip()) : itemRepository.count();

        return items.collectList()
                .flatMap(pageItems -> total.flatMap(count -> toPage(pageItems, count, pageable)));
    }

    public Mono<ItemDto> getItem(long id) {
        return itemRepository.findById(id)
                .switchIfEmpty(Mono.error(() -> new ItemNotFoundException(id)))
                .flatMap(item -> cartService.getCount(id).map(count -> ItemMapper.toDto(item, count)));
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

    private Mono<ItemsPage> toPage(List<Item> items, long total, Pageable pageable) {
        List<Long> ids = items.stream().map(Item::getId).toList();
        Paging paging = new Paging(
                pageable.getPageSize(),
                pageable.getPageNumber() + 1,
                pageable.hasPrevious(),
                pageable.getOffset() + items.size() < total
        );
        return cartService.getCounts(ids)
                .map(counts -> items.stream()
                        .map(item -> ItemMapper.toDto(item, counts.getOrDefault(item.getId(), 0)))
                        .toList())
                .map(dtos -> new ItemsPage(dtos, paging));
    }
}
