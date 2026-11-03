package ru.yandex.practicum.mymarket.controller;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartActionForm;
import ru.yandex.practicum.mymarket.dto.CartItemActionForm;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsQuery;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ShowcaseService;

import java.util.ArrayList;
import java.util.List;

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
    public Mono<Rendering> getItems(@ModelAttribute ItemsQuery query) {
        return showcaseService.findItems(query.search(), query.sort(), query.pageNumber(), query.pageSize())
                .map(page -> Rendering.view("items")
                        .modelAttribute("items", toRows(page.items()))
                        .modelAttribute("search", query.search())
                        .modelAttribute("sort", query.sort().name())
                        .modelAttribute("paging", page.paging())
                        .build());
    }

    @PostMapping("/items")
    public Mono<String> changeCartQuantityFromItems(@Valid @ModelAttribute CartItemActionForm form,
                                                    @ModelAttribute ItemsQuery query) {
        return cartService.changeQuantity(form.id(), form.action())
                .thenReturn("redirect:" + itemsUrl(query));
    }

    @GetMapping("/items/{id}")
    public Mono<Rendering> getItem(@PathVariable long id) {
        return showcaseService.getItem(id).map(ItemController::renderItem);
    }

    @PostMapping("/items/{id}")
    public Mono<Rendering> changeCartQuantityFromItem(@PathVariable long id,
                                                      @Valid @ModelAttribute CartActionForm form) {
        return cartService.changeQuantity(id, form.action())
                .then(Mono.defer(() -> showcaseService.getItem(id)))
                .map(ItemController::renderItem);
    }

    private static Rendering renderItem(ItemDto item) {
        return Rendering.view("item").modelAttribute("item", item).build();
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
