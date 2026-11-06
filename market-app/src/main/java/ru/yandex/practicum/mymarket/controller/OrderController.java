package ru.yandex.practicum.mymarket.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.security.MarketUserDetails;
import ru.yandex.practicum.mymarket.service.OrderService;

/**
 * Заказы вошедшего покупателя: в списке только его заказы, чужой заказ по номеру — 404.
 */
@Controller
@RequestMapping("/orders")
@PreAuthorize("isAuthenticated()")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public Mono<Rendering> getOrders(@AuthenticationPrincipal MarketUserDetails user) {
        return orderService.getOrders(user.getId())
                .collectList()
                .map(orders -> Rendering.view("orders").modelAttribute("orders", orders).build());
    }

    @GetMapping("/{id}")
    public Mono<Rendering> getOrder(@PathVariable long id,
                                    @RequestParam(defaultValue = "false") boolean newOrder,
                                    @AuthenticationPrincipal MarketUserDetails user) {
        return orderService.getOrder(user.getId(), id)
                .map(order -> Rendering.view("order")
                        .modelAttribute("order", order)
                        .modelAttribute("newOrder", newOrder)
                        .build());
    }
}
