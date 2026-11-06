package ru.yandex.practicum.mymarket.service;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemSummary;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.mapper.ItemMapper;
import ru.yandex.practicum.mymarket.model.ItemSort;

import java.util.List;
import java.util.Map;

/**
 * Витрина — фасад над модулями каталога и корзины: товары берутся из {@link ItemService} (через кеш Redis),
 * количество в корзине — из {@link CartService}. Каталог о корзине не знает.
 * Витрину смотрят и анонимные посетители: у них нет корзины ({@code userId == null}), количество всегда 0.
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

    /**
     * @param userId id покупателя или {@code null} для анонимного посетителя
     */
    public Mono<ItemsPage> findItems(@Nullable Long userId, String search, ItemSort sort, int pageNumber, int pageSize) {
        return itemService.findItems(search, sort, pageNumber, pageSize)
                .flatMap(page -> counts(userId, page)
                        .map(counts -> toItemsPage(page, counts)));
    }

    /**
     * @param userId id покупателя или {@code null} для анонимного посетителя
     */
    public Mono<ItemDto> getItem(@Nullable Long userId, long id) {
        return itemService.getItem(id)
                .flatMap(item -> (userId == null ? Mono.just(0) : cartService.getCount(userId, id))
                        .map(count -> ItemMapper.toDto(item, count)));
    }

    private Mono<Map<Long, Integer>> counts(@Nullable Long userId, CatalogPage page) {
        if (userId == null) {
            return Mono.just(Map.of());
        }
        return cartService.getCounts(userId, page.items().stream().map(ItemSummary::id).toList());
    }

    private static ItemsPage toItemsPage(CatalogPage page, Map<Long, Integer> counts) {
        List<ItemDto> items = page.items().stream()
                .map(item -> ItemMapper.toDto(item, counts.getOrDefault(item.id(), 0)))
                .toList();
        Paging paging = new Paging(page.pageSize(), page.pageNumber(), page.hasPrevious(), page.hasNext());
        return new ItemsPage(items, paging);
    }
}
