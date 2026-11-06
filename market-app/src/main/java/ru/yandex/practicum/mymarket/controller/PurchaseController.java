package ru.yandex.practicum.mymarket.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.security.MarketUserDetails;
import ru.yandex.practicum.mymarket.service.PurchaseService;

/**
 * Оформление заказа из корзины вошедшего покупателя с оплатой с его счёта.
 */
@Controller
@PreAuthorize("isAuthenticated()")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/buy")
    public Mono<String> buy(@AuthenticationPrincipal MarketUserDetails user) {
        return purchaseService.buy(user.getId(), user.getUsername())
                .map(orderId -> "redirect:/orders/" + orderId + "?newOrder=true");
    }
}
