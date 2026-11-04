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

    /**
     * Уменьшает количество на единицу. Количество позиции корзины не бывает меньше 1,
     * поэтому последняя единица не вычитается: позицию нужно удалить из корзины.
     *
     * @return {@code false}, если осталась последняя единица и количество не изменилось
     */
    public boolean decrement() {
        if (quantity == 1) {
            return false;
        }
        quantity--;
        return true;
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
