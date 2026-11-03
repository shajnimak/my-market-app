package ru.yandex.practicum.mymarket.dto;

import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.mymarket.model.CartAction;

/**
 * Изменение количества товара в корзине с витрины или со страницы корзины.
 */
public record CartItemActionForm(@NotNull Long id, @NotNull CartAction action) {
}
