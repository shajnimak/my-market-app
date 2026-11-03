package ru.yandex.practicum.mymarket.mapper;

import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.CartLine;
import ru.yandex.practicum.mymarket.model.Item;

public final class ItemMapper {

    private ItemMapper() {
    }

    public static ItemDto toDto(Item item, int count) {
        return new ItemDto(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                imagePath(item.getId()),
                item.getPrice(),
                count
        );
    }

    public static ItemDto toDto(CartLine cartLine) {
        return new ItemDto(
                cartLine.itemId(),
                cartLine.title(),
                cartLine.description(),
                imagePath(cartLine.itemId()),
                cartLine.price(),
                cartLine.quantity()
        );
    }

    public static String imagePath(long itemId) {
        return "images/" + itemId;
    }
}
