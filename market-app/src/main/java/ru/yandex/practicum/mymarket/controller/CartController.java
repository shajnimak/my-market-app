package ru.yandex.practicum.mymarket.controller;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartItemActionForm;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.PurchaseService;

@Controller
@RequestMapping("/cart/items")
public class CartController {

    private final CartService cartService;
    private final PurchaseService purchaseService;

    public CartController(CartService cartService, PurchaseService purchaseService) {
        this.cartService = cartService;
        this.purchaseService = purchaseService;
    }

    /**
     * Корзина и доступность оформления заказа: баланс запрашивается в сервисе платежей.
     */
    @GetMapping
    public Mono<Rendering> getCart() {
        return cartService.getCart()
                .flatMap(cart -> purchaseService.getCheckout(cart.total())
                        .map(checkout -> Rendering.view("cart")
                                .modelAttribute("items", cart.items())
                                .modelAttribute("total", cart.total())
                                .modelAttribute("checkout", checkout)
                                .build()));
    }

    /**
     * Post/Redirect/Get: после изменения корзины браузер перезапрашивает её через GET,
     * поэтому обновление страницы не отправит форму повторно.
     */
    @PostMapping
    public Mono<String> changeCartQuantity(@Valid @ModelAttribute CartItemActionForm form) {
        return cartService.changeQuantity(form.id(), form.action())
                .thenReturn("redirect:/cart/items");
    }
}
