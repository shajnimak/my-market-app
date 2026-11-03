package ru.yandex.practicum.mymarket.dto;

import java.util.List;

public record CartDto(List<ItemDto> items, long total) {
}
