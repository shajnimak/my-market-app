package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.ItemSort;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ItemControllerTest extends AbstractControllerTest {

    private static final ItemDto BALL = new ItemDto(1, "Мяч", "Футбольный", "images/1", 2500, 0);
    private static final ItemDto RACKET = new ItemDto(2, "Ракетка", "Теннисная", "images/2", 7800, 2);
    private static final ItemDto SKI = new ItemDto(3, "Лыжи", "Беговые", "images/3", 12900, 0);
    private static final ItemDto SKATES = new ItemDto(4, "Коньки", "Фигурные", "images/4", 6400, 1);

    @Test
    void getItems_shouldUseDefaultsAndGroupItemsIntoRowsOfThree() throws Exception {
        Paging paging = new Paging(5, 1, false, true);
        when(showcaseService.findItems("", ItemSort.NO, 1, 5))
                .thenReturn(new ItemsPage(List.of(BALL, RACKET, SKI, SKATES), paging));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("items"))
                .andExpect(model().attribute("items", List.of(
                        List.of(BALL, RACKET, SKI),
                        List.of(SKATES, ItemDto.placeholder(), ItemDto.placeholder())
                )))
                .andExpect(model().attribute("search", ""))
                .andExpect(model().attribute("sort", "NO"))
                .andExpect(model().attribute("paging", paging))
                .andExpect(content().string(containsString("Ракетка")));
    }

    @Test
    void getItems_shouldPassRequestParametersToService() throws Exception {
        Paging paging = new Paging(10, 2, true, false);
        when(showcaseService.findItems("мяч", ItemSort.PRICE, 2, 10))
                .thenReturn(new ItemsPage(List.of(BALL), paging));

        mockMvc.perform(get("/items")
                        .param("search", "мяч")
                        .param("sort", "PRICE")
                        .param("pageNumber", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("items"))
                .andExpect(model().attribute("search", "мяч"))
                .andExpect(model().attribute("sort", "PRICE"))
                .andExpect(model().attribute("paging", paging))
                .andExpect(content().string(containsString("<option value=\"PRICE\" selected=\"selected\">")))
                .andExpect(content().string(containsString("<option value=\"10\" selected=\"selected\">")));
    }

    @Test
    void getItems_shouldReturnBadRequest_whenSortUnknown() throws Exception {
        mockMvc.perform(get("/items").param("sort", "COLOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeCartQuantityFromItems_shouldChangeCartAndRedirectBackWithParams() throws Exception {
        mockMvc.perform(post("/items")
                        .param("id", "2")
                        .param("action", "PLUS")
                        .param("search", "мяч")
                        .param("sort", "ALPHA")
                        .param("pageNumber", "3")
                        .param("pageSize", "20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/items?search=%D0%BC%D1%8F%D1%87&sort=ALPHA&pageNumber=3&pageSize=20"));

        verify(cartService).changeQuantity(2, CartAction.PLUS);
    }

    @Test
    void changeCartQuantityFromItems_shouldUseDefaultParams() throws Exception {
        mockMvc.perform(post("/items").param("id", "2").param("action", "MINUS"))
                .andExpect(redirectedUrl("/items?search=&sort=NO&pageNumber=1&pageSize=5"));

        verify(cartService).changeQuantity(2, CartAction.MINUS);
    }

    @Test
    void changeCartQuantityFromItems_shouldReturnBadRequest_whenActionMissing() throws Exception {
        mockMvc.perform(post("/items").param("id", "2"))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).changeQuantity(anyLong(), any());
    }

    @Test
    void getItem_shouldRenderItemPage() throws Exception {
        when(showcaseService.getItem(2)).thenReturn(RACKET);

        mockMvc.perform(get("/items/2"))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attribute("item", RACKET))
                .andExpect(content().string(containsString("7800 руб.")));
    }

    @Test
    void getItem_shouldReturnNotFound_whenItemMissing() throws Exception {
        when(showcaseService.getItem(99)).thenThrow(new ItemNotFoundException(99));

        mockMvc.perform(get("/items/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeCartQuantityFromItem_shouldChangeCartAndRenderItemPage() throws Exception {
        when(showcaseService.getItem(2)).thenReturn(RACKET);

        mockMvc.perform(post("/items/2").param("action", "PLUS"))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attribute("item", RACKET));

        verify(cartService).changeQuantity(2, CartAction.PLUS);
    }
}
