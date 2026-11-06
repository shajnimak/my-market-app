package ru.yandex.practicum.mymarket.dto;

import ru.yandex.practicum.mymarket.model.ItemSort;

/**
 * Параметры витрины: строка поиска, сортировка и пагинация. Отсутствующие параметры заменяются значениями по умолчанию.
 */
public record ItemsQuery(String search, ItemSort sort, Integer pageNumber, Integer pageSize) {

    public ItemsQuery {
        search = search == null ? "" : search;
        sort = sort == null ? ItemSort.NO : sort;
        pageNumber = pageNumber == null ? 1 : pageNumber;
        pageSize = pageSize == null ? 5 : pageSize;
    }
}
