package ru.yandex.practicum.mymarket.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
import java.util.Map;

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

    public ItemsPage findItems(String search, ItemSort sort, int pageNumber, int pageSize) {
        int number = Math.max(pageNumber, 1);
        int size = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(number - 1, size, sort.toSort());

        Page<Item> page = StringUtils.hasText(search)
                ? itemRepository.search(search.strip(), pageable)
                : itemRepository.findAll(pageable);

        Map<Long, Integer> counts = cartService.getCounts(page.map(Item::getId).toList());
        List<ItemDto> items = page.stream()
                .map(item -> ItemMapper.toDto(item, counts.getOrDefault(item.getId(), 0)))
                .toList();

        return new ItemsPage(items, new Paging(size, number, page.hasPrevious(), page.hasNext()));
    }

    public ItemDto getItem(long id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));
        return ItemMapper.toDto(item, cartService.getCount(id));
    }

    public ImageData getImage(long itemId) {
        return itemImageRepository.findById(itemId)
                .map(image -> new ImageData(image.getContentType(), image.getContent()))
                .orElseThrow(() -> new ImageNotFoundException(itemId));
    }

    public boolean isCatalogEmpty() {
        return itemRepository.count() == 0;
    }

    @Transactional
    public long createItem(NewItem newItem) {
        Item item = itemRepository.save(new Item(newItem.title(), newItem.description(), newItem.price()));
        ImageData image = newItem.image();
        itemImageRepository.save(new ItemImage(item.getId(), image.contentType(), image.content()));
        return item.getId();
    }
}
