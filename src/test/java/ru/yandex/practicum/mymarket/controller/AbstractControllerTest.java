package ru.yandex.practicum.mymarket.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PurchaseService;

/**
 * Общая конфигурация веб-слоя: все контроллеры и одинаковый набор моков сервисов,
 * благодаря чему контекст создаётся один раз и переиспользуется всеми наследниками.
 */
@WebMvcTest
public abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected ItemService itemService;

    @MockitoBean
    protected CartService cartService;

    @MockitoBean
    protected OrderService orderService;

    @MockitoBean
    protected PurchaseService purchaseService;
}
