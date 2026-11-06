package ru.yandex.practicum.mymarket.dto;

/**
 * Товар в списке витрины — то, что хранится в кеше Redis для страниц поиска:
 * идентификатор, название, описание и цена.
 */
public record ItemSummary(long id, String title, String description, long price) {
}
