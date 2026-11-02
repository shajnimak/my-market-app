package ru.yandex.practicum.mymarket.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Картинка товара. Хранится отдельно от {@link Item}, чтобы не загружать байты при выводе витрины.
 */
@Entity
@Table(name = "item_images")
public class ItemImage {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Lob
    @Column(nullable = false)
    private byte[] content;

    protected ItemImage() {
    }

    public ItemImage(Long itemId, String contentType, byte[] content) {
        this.itemId = itemId;
        this.contentType = contentType;
        this.content = content;
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
