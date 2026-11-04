package ru.yandex.practicum.mymarket.service;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.mapper.ItemMapper;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemSort;

import java.util.List;
import java.util.Map;

/**
 * Витрина — фасад над модулями каталога и корзины: товары берутся из {@link ItemService},
 * количество в корзине — из {@link CartService}. Сами модули друг о друге не знают.
 */
@Service
@Transactional(readOnly = true)
public class ShowcaseService {

    private final ItemService itemService;
    private final CartService cartService;

    public ShowcaseService(ItemService itemService, CartService cartService) {
        this.itemService = itemService;
        this.cartService = cartService;
    }

    public Mono<ItemsPage> findItems(String search, ItemSort sort, int pageNumber, int pageSize) {
        return itemService.findItems(search, sort, pageNumber, pageSize)
                .flatMap(page -> cartService.getCounts(page.map(Item::getId).toList())
                        .map(counts -> toItemsPage(page, counts)));
    }

    public Mono<ItemDto> getItem(long id) {
        return itemService.getItem(id)
                .flatMap(item -> cartService.getCount(id).map(count -> ItemMapper.toDto(item, count)));
    }

    private static ItemsPage toItemsPage(Page<Item> page, Map<Long, Integer> counts) {
        List<ItemDto> items = page.stream()
                .map(item -> ItemMapper.toDto(item, counts.getOrDefault(item.getId(), 0)))
                .toList();
        Paging paging = new Paging(page.getSize(), page.getNumber() + 1, page.hasPrevious(), page.hasNext());
        return new ItemsPage(items, paging);
    }
}
