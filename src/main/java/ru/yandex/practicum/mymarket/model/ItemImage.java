package ru.yandex.practicum.mymarket.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Картинка товара. Хранится отдельно от {@link Item}, чтобы не загружать байты при выводе витрины.
 * Идентификатор совпадает с id товара и не генерируется базой, поэтому новизну записи
 * для Spring Data определяет флаг {@link #isNew()}.
 */
@Table("item_images")
public class ItemImage implements Persistable<Long> {

    @Id
    private Long itemId;

    private String contentType;

    private byte[] content;

    @Transient
    private boolean newImage;

    protected ItemImage() {
    }

    private ItemImage(Long itemId, String contentType, byte[] content) {
        this.itemId = itemId;
        this.contentType = contentType;
        this.content = content;
        this.newImage = true;
    }

    public static ItemImage newImage(Long itemId, String contentType, byte[] content) {
        return new ItemImage(itemId, contentType, content);
    }

    @Override
    public Long getId() {
        return itemId;
    }

    @Override
    public boolean isNew() {
        return newImage;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }
}
