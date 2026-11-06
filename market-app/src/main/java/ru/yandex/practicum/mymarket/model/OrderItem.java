package ru.yandex.practicum.mymarket.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Позиция заказа. Название и цена копируются из товара в момент покупки,
 * чтобы последующее изменение товара не меняло уже оформленные заказы.
 */
@Table("order_items")
public class OrderItem {

    @Id
    private Long id;

    private Long orderId;

    private Long itemId;

    private String title;

    private long price;

    private int quantity;

    protected OrderItem() {
    }

    public OrderItem(Long orderId, CartLine cartLine) {
        this.orderId = orderId;
        this.itemId = cartLine.itemId();
        this.title = cartLine.title();
        this.price = cartLine.price();
        this.quantity = cartLine.quantity();
    }

    public long getTotalPrice() {
        return price * quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getTitle() {
        return title;
    }

    public long getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }
}
