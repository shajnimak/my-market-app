package ru.yandex.practicum.mymarket.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("cart_items")
public class CartItem {

    @Id
    private Long id;

    private Long itemId;

    private int quantity;

    protected CartItem() {
    }

    public CartItem(Long itemId) {
        this.itemId = itemId;
        this.quantity = 1;
    }

    public void increment() {
        quantity++;
    }

    public void decrement() {
        if (quantity <= 1) {
            throw new IllegalStateException("Quantity of the cart item can't be less than 1");
        }
        quantity--;
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }
}
