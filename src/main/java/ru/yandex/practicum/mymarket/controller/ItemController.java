package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.ItemSort;
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
    public String getItems(@RequestParam(defaultValue = "") String search,
                           @RequestParam(defaultValue = "NO") ItemSort sort,
                           @RequestParam(defaultValue = "1") int pageNumber,
                           @RequestParam(defaultValue = "5") int pageSize,
                           Model model) {
        ItemsPage page = showcaseService.findItems(search, sort, pageNumber, pageSize);
        model.addAttribute("items", toRows(page.items()));
        model.addAttribute("search", search);
        model.addAttribute("sort", sort.name());
        model.addAttribute("paging", page.paging());
        return "items";
    }

    @PostMapping("/items")
    public String changeCartQuantityFromItems(@RequestParam long id,
                                              @RequestParam CartAction action,
                                              @RequestParam(defaultValue = "") String search,
                                              @RequestParam(defaultValue = "NO") ItemSort sort,
                                              @RequestParam(defaultValue = "1") int pageNumber,
                                              @RequestParam(defaultValue = "5") int pageSize,
                                              RedirectAttributes redirectAttributes) {
        cartService.changeQuantity(id, action);
        redirectAttributes.addAttribute("search", search);
        redirectAttributes.addAttribute("sort", sort);
        redirectAttributes.addAttribute("pageNumber", pageNumber);
        redirectAttributes.addAttribute("pageSize", pageSize);
        return "redirect:/items";
    }

    @GetMapping("/items/{id}")
    public String getItem(@PathVariable long id, Model model) {
        model.addAttribute("item", showcaseService.getItem(id));
        return "item";
    }

    @PostMapping("/items/{id}")
    public String changeCartQuantityFromItem(@PathVariable long id,
                                             @RequestParam CartAction action,
                                             Model model) {
        cartService.changeQuantity(id, action);
        model.addAttribute("item", showcaseService.getItem(id));
        return "item";
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
