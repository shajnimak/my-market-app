package ru.yandex.practicum.mymarket.dto;

/**
 * Карточка товара — то, что хранится в кеше Redis для страницы товара и корзины:
 * идентификатор, картинка (ссылка на неё), название, цена и описание.
 */
public record ItemCard(long id, String title, String description, String imgPath, long price) {
}
