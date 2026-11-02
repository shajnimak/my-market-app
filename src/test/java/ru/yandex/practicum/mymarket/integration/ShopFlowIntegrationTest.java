package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderItemDto;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий: витрина → корзина → покупка → заказы.
 */
class ShopFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ItemService itemService;

    private long ballId;
    private long racketId;
    private long basketballId;

    @BeforeEach
    void setUp() {
        ImageData image = new ImageData("image/png", new byte[]{1, 2, 3});
        ballId = itemService.createItem(new NewItem("Футбольный мяч", "Размер 5", 2500, image));
        racketId = itemService.createItem(new NewItem("Ракетка", "Теннисная", 7800, image));
        basketballId = itemService.createItem(new NewItem("Баскетбольный мяч", "Для зала", 3200, image));
    }

    @Test
    void itemsPage_shouldFilterSortAndPaginate() throws Exception {
        mockMvc.perform(get("/items")
                        .param("search", "МЯЧ")
                        .param("sort", "PRICE")
                        .param("pageSize", "1")
                        .param("pageNumber", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paging", new Paging(1, 2, true, false)))
                .andExpect(model().attribute("items", contains(contains(
                        new ItemDto(basketballId, "Баскетбольный мяч", "Для зала", "images/" + basketballId, 3200, 0),
                        ItemDto.placeholder(),
                        ItemDto.placeholder()))));
    }

    @Test
    void imageEndpoint_shouldServeStoredImage() throws Exception {
        mockMvc.perform(get("/images/{id}", ballId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void cart_shouldReflectChangesFromAllPages() throws Exception {
        mockMvc.perform(post("/items").param("id", String.valueOf(ballId)).param("action", "PLUS"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/items/{id}", ballId).param("action", "PLUS"))
                .andExpect(model().attribute("item", itemWithCount(ballId, 2)));
        mockMvc.perform(post("/cart/items").param("id", String.valueOf(racketId)).param("action", "PLUS"))
                .andExpect(model().attribute("total", 2 * 2500L + 7800L));

        mockMvc.perform(post("/cart/items").param("id", String.valueOf(ballId)).param("action", "MINUS"))
                .andExpect(model().attribute("total", 2500L + 7800L));
        mockMvc.perform(post("/cart/items").param("id", String.valueOf(racketId)).param("action", "DELETE"))
                .andExpect(model().attribute("items", hasSize(1)))
                .andExpect(model().attribute("total", 2500L));

        mockMvc.perform(get("/items/{id}", ballId))
                .andExpect(model().attribute("item", itemWithCount(ballId, 1)));
    }

    @Test
    void buy_shouldCreateOrderFromCartAndEmptyCart() throws Exception {
        mockMvc.perform(post("/items/{id}", ballId).param("action", "PLUS"));
        mockMvc.perform(post("/items/{id}", ballId).param("action", "PLUS"));
        mockMvc.perform(post("/items/{id}", racketId).param("action", "PLUS"));

        String location = mockMvc.perform(post("/buy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*?newOrder=true"))
                .andReturn().getResponse().getRedirectedUrl();
        long orderId = Long.parseLong(location.substring("/orders/".length(), location.indexOf('?')));

        OrderDto expectedOrder = new OrderDto(orderId, List.of(
                new OrderItemDto(ballId, "Футбольный мяч", 2500, 2),
                new OrderItemDto(racketId, "Ракетка", 7800, 1)
        ), 12800);
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(model().attribute("order", expectedOrder))
                .andExpect(model().attribute("newOrder", true));
        mockMvc.perform(get("/orders"))
                .andExpect(model().attribute("orders", hasItem(expectedOrder)));
        mockMvc.perform(get("/cart/items"))
                .andExpect(model().attribute("items", hasSize(0)))
                .andExpect(model().attribute("total", 0L));
    }

    @Test
    void buy_shouldFail_whenCartEmpty() throws Exception {
        mockMvc.perform(post("/buy"))
                .andExpect(status().isBadRequest());
    }

    private ItemDto itemWithCount(long id, int count) {
        ItemDto item = itemService.getItem(id);
        return new ItemDto(item.id(), item.title(), item.description(), item.imgPath(), item.price(), count);
    }
}
