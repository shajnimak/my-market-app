package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.exception.EmptyCartException;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PurchaseService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void buy_shouldPersistOrderAndClearCart() {
        long itemId = itemService.createItem(new NewItem("Мяч", "Описание", 1500,
                new ImageData("image/png", new byte[]{1})));
        cartService.changeQuantity(itemId, CartAction.PLUS);
        cartService.changeQuantity(itemId, CartAction.PLUS);

        long orderId = purchaseService.buy();

        OrderDto order = orderService.getOrder(orderId);
        assertThat(order.totalSum()).isEqualTo(3000);
        assertThat(order.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(itemId);
                    assertThat(item.count()).isEqualTo(2);
                });
        assertThat(cartItemRepository.count()).isZero();
    }

    @Test
    void buy_shouldNotCreateOrder_whenCartEmpty() {
        long ordersBefore = orderRepository.count();

        assertThatThrownBy(() -> purchaseService.buy()).isInstanceOf(EmptyCartException.class);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }
}
