package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.time.Duration;

/**
 * Отдаёт картинки товаров. Картинки встраиваются в страницы через {@code <img>}, поэтому ответ
 * помечается как вложение и запрещает исполнение активного содержимого: даже SVG из демо-каталога,
 * открытый напрямую по ссылке, не сможет выполнить скрипт в origin приложения.
 */
@RestController
public class ImageController {

    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    private final ItemService itemService;

    public ImageController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/images/{itemId}")
    public ResponseEntity<byte[]> getImage(@PathVariable long itemId) {
        ImageData image = itemService.getImage(itemId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .headers(ImageController::protectFromActiveContent)
                .body(image.content());
    }

    private static void protectFromActiveContent(HttpHeaders headers) {
        headers.setContentDisposition(ContentDisposition.attachment().build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
    }
}
