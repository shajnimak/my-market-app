package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.CartAction;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

class CartControllerTest extends AbstractControllerTest {

    private static final ItemDto BALL = new ItemDto(1, "Мяч", "Футбольный", "images/1", 2500, 2);

    @Test
    void getCart_shouldRenderItemsAndTotal() throws Exception {
        when(cartService.getCart()).thenReturn(new CartDto(List.of(BALL), 5000));

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attribute("items", List.of(BALL)))
                .andExpect(model().attribute("total", 5000L))
                .andExpect(content().string(containsString("Итого: 5000 руб.")));
    }

    @Test
    void getCart_shouldHideBuyButton_whenCartEmpty() throws Exception {
        when(cartService.getCart()).thenReturn(new CartDto(List.of(), 0));

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("action=\"/buy\""))));
    }

    @Test
    void changeCartQuantity_shouldApplyActionAndRedirectToCart() throws Exception {
        mockMvc.perform(post("/cart/items").param("id", "1").param("action", "DELETE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart/items"));

        verify(cartService).changeQuantity(1, CartAction.DELETE);
        verify(cartService, never()).getCart();
    }

    @Test
    void changeCartQuantity_shouldReturnBadRequest_whenActionUnknown() throws Exception {
        mockMvc.perform(post("/cart/items").param("id", "1").param("action", "TWICE"))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).changeQuantity(anyLong(), any());
    }
}
