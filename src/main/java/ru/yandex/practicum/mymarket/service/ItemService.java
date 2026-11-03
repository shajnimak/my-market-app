package ru.yandex.practicum.mymarket.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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

    public Page<Item> findItems(String search, ItemSort sort, int pageNumber, int pageSize) {
        int number = Math.max(pageNumber, 1);
        int size = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(number - 1, size, sort.toSort());

        return StringUtils.hasText(search)
                ? itemRepository.search(search.strip(), pageable)
                : itemRepository.findAll(pageable);
    }

    public Item getItem(long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));
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
