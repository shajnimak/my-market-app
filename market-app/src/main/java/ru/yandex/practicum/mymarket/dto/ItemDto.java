package ru.yandex.practicum.mymarket.dto;

public record ItemDto(long id, String title, String description, String imgPath, long price, int count) {

    private static final ItemDto PLACEHOLDER = new ItemDto(-1, "", "", "", 0, 0);

    /**
     * Заглушка для пустой ячейки в строке витрины.
     */
    public static ItemDto placeholder() {
        return PLACEHOLDER;
    }
}
