package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import ru.yandex.practicum.mymarket.service.PurchaseService;

@Controller
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/buy")
    public String buy() {
        long orderId = purchaseService.buy();
        return "redirect:/orders/" + orderId + "?newOrder=true";
    }
}
