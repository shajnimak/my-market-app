package ru.yandex.practicum.mymarket.dto;

import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.mymarket.model.CartAction;

/**
 * Изменение количества товара в корзине со страницы товара (id товара берётся из пути).
 */
public record CartActionForm(@NotNull CartAction action) {
}
