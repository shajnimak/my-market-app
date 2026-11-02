package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseControllerTest extends AbstractControllerTest {

    @Test
    void buy_shouldRedirectToNewOrder() throws Exception {
        when(purchaseService.buy()).thenReturn(12L);

        mockMvc.perform(post("/buy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/12?newOrder=true"));
    }

    @Test
    void buy_shouldReturnBadRequest_whenCartEmpty() throws Exception {
        when(purchaseService.buy()).thenThrow(new EmptyCartException());

        mockMvc.perform(post("/buy"))
                .andExpect(status().isBadRequest());
    }
}
