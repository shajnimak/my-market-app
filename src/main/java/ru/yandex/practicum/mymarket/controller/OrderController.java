package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.yandex.practicum.mymarket.service.OrderService;

@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public String getOrders(Model model) {
        model.addAttribute("orders", orderService.getOrders());
        return "orders";
    }

    @GetMapping("/{id}")
    public String getOrder(@PathVariable long id,
                           @RequestParam(defaultValue = "false") boolean newOrder,
                           Model model) {
        model.addAttribute("order", orderService.getOrder(id));
        model.addAttribute("newOrder", newOrder);
        return "order";
    }
}
