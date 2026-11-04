package ru.yandex.practicum.mymarket.dto;

import java.util.List;

public record ItemsPage(List<ItemDto> items, Paging paging) {
}
