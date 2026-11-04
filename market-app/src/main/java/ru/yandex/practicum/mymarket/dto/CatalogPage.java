package ru.yandex.practicum.mymarket.dto;

import java.util.List;

/**
 * Страница результатов поиска по каталогу. Кешируется в Redis целиком для каждой
 * комбинации строки поиска, сортировки и пагинации.
 *
 * @param items      товары страницы
 * @param pageNumber номер страницы, начиная с 1
 * @param pageSize   размер страницы
 * @param totalItems сколько всего товаров подходит под поиск
 */
public record CatalogPage(List<ItemSummary> items, int pageNumber, int pageSize, long totalItems) {

    public boolean hasPrevious() {
        return pageNumber > 1;
    }

    public boolean hasNext() {
        return (long) pageNumber * pageSize < totalItems;
    }
}
