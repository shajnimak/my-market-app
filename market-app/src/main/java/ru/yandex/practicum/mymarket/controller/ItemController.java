package ru.yandex.practicum.mymarket.controller;

import jakarta.validation.Valid;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartActionForm;
import ru.yandex.practicum.mymarket.dto.CartItemActionForm;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsQuery;
import ru.yandex.practicum.mymarket.security.MarketUserDetails;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ShowcaseService;

import java.util.ArrayList;
import java.util.List;

/**
 * Витрина и карточка товара. Смотреть их может и анонимный посетитель (без корзины, количество 0),
 * менять корзину — только вошедший покупатель, и только свою. Все POST-действия с корзиной
 * завершаются редиректом (Post/Redirect/Get).
 */
@Controller
public class ItemController {

    static final int ITEMS_PER_ROW = 3;

    private final ShowcaseService showcaseService;
    private final CartService cartService;

    public ItemController(ShowcaseService showcaseService, CartService cartService) {
        this.showcaseService = showcaseService;
        this.cartService = cartService;
    }

    @GetMapping({"/", "/items"})
    public Mono<Rendering> getItems(@ModelAttribute ItemsQuery query,
                                    @RequestParam MultiValueMap<String, String> params,
                                    @AuthenticationPrincipal MarketUserDetails user) {
        return showcaseService.findItems(userId(user), query.search(), query.sort(), query.pageNumber(), query.pageSize())
                .map(page -> Rendering.view("items")
                        .modelAttribute("items", toRows(page.items()))
                        .modelAttribute("search", query.search())
                        .modelAttribute("sort", query.sort().name())
                        .modelAttribute("paging", page.paging())
                        .modelAttribute("loggedOut", params.containsKey("logout"))
                        .build());
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public Mono<String> changeCartQuantityFromItems(@Valid @ModelAttribute CartItemActionForm form,
                                                    @ModelAttribute ItemsQuery query,
                                                    @AuthenticationPrincipal MarketUserDetails user) {
        return cartService.changeQuantity(user.getId(), form.id(), form.action())
                .thenReturn("redirect:" + itemsUrl(query));
    }

    @GetMapping("/items/{id}")
    public Mono<Rendering> getItem(@PathVariable long id, @AuthenticationPrincipal MarketUserDetails user) {
        return showcaseService.getItem(userId(user), id)
                .map(item -> Rendering.view("item").modelAttribute("item", item).build());
    }

    @PostMapping("/items/{id}")
    @PreAuthorize("isAuthenticated()")
    public Mono<String> changeCartQuantityFromItem(@PathVariable long id,
                                                   @Valid @ModelAttribute CartActionForm form,
                                                   @AuthenticationPrincipal MarketUserDetails user) {
        return cartService.changeQuantity(user.getId(), id, form.action())
                .thenReturn("redirect:/items/" + id);
    }

    @Nullable
    private static Long userId(@Nullable MarketUserDetails user) {
        return user == null ? null : user.getId();
    }

    private static String itemsUrl(ItemsQuery query) {
        return UriComponentsBuilder.fromPath("/items")
                .queryParam("search", query.search())
                .queryParam("sort", query.sort())
                .queryParam("pageNumber", query.pageNumber())
                .queryParam("pageSize", query.pageSize())
                .encode()
                .toUriString();
    }

    private static List<List<ItemDto>> toRows(List<ItemDto> items) {
        List<List<ItemDto>> rows = new ArrayList<>();
        for (int from = 0; from < items.size(); from += ITEMS_PER_ROW) {
            List<ItemDto> row = new ArrayList<>(items.subList(from, Math.min(from + ITEMS_PER_ROW, items.size())));
            while (row.size() < ITEMS_PER_ROW) {
                row.add(ItemDto.placeholder());
            }
            rows.add(row);
        }
        return rows;
    }
}
