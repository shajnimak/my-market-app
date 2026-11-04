package ru.yandex.practicum.mymarket.mapper;

import ru.yandex.practicum.mymarket.dto.ItemCard;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemSummary;
import ru.yandex.practicum.mymarket.model.CartLine;
import ru.yandex.practicum.mymarket.model.Item;

public final class ItemMapper {

    private ItemMapper() {
    }

    public static ItemCard toCard(Item item) {
        return new ItemCard(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                imagePath(item.getId()),
                item.getPrice()
        );
    }

    public static ItemSummary toSummary(Item item) {
        return new ItemSummary(item.getId(), item.getTitle(), item.getDescription(), item.getPrice());
    }

    public static ItemDto toDto(ItemCard card, int count) {
        return new ItemDto(card.id(), card.title(), card.description(), card.imgPath(), card.price(), count);
    }

    public static ItemDto toDto(ItemSummary summary, int count) {
        return new ItemDto(
                summary.id(),
                summary.title(),
                summary.description(),
                imagePath(summary.id()),
                summary.price(),
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
