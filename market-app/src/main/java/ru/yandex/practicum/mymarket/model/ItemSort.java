package ru.yandex.practicum.mymarket.model;

import org.springframework.data.domain.Sort;

public enum ItemSort {
    NO(Sort.by("id")),
    ALPHA(Sort.by("title").and(Sort.by("id"))),
    PRICE(Sort.by("price").and(Sort.by("id")));

    private final Sort sort;

    ItemSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
