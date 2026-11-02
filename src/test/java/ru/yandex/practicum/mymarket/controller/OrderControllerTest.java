package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.exception.OrderNotFoundException;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class OrderControllerTest extends AbstractControllerTest {

    private static final OrderDto ORDER = new OrderDto(7,
            List.of(new OrderItemDto(1, "Мяч", 2500, 2), new OrderItemDto(2, "Ракетка", 7800, 1)),
            12800);

    @Test
    void getOrders_shouldRenderAllOrders() throws Exception {
        when(orderService.getOrders()).thenReturn(List.of(ORDER));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(model().attribute("orders", List.of(ORDER)))
                .andExpect(content().string(containsString("Заказ №7")))
                .andExpect(content().string(containsString("Мяч (2 шт.) 5000 руб.")));
    }

    @Test
    void getOrder_shouldRenderOrderWithoutCongratulation_byDefault() throws Exception {
        when(orderService.getOrder(7)).thenReturn(ORDER);

        mockMvc.perform(get("/orders/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("order"))
                .andExpect(model().attribute("order", ORDER))
                .andExpect(model().attribute("newOrder", false))
                .andExpect(content().string(containsString("Сумма: 12800 руб.")))
                .andExpect(content().string(not(containsString("Поздравляем"))));
    }

    @Test
    void getOrder_shouldShowCongratulation_forNewOrder() throws Exception {
        when(orderService.getOrder(7)).thenReturn(ORDER);

        mockMvc.perform(get("/orders/7").param("newOrder", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("newOrder", true))
                .andExpect(content().string(containsString("Поздравляем")));
    }

    @Test
    void getOrder_shouldReturnNotFound_whenOrderMissing() throws Exception {
        when(orderService.getOrder(8)).thenThrow(new OrderNotFoundException(8));

        mockMvc.perform(get("/orders/8"))
                .andExpect(status().isNotFound());
    }
}
