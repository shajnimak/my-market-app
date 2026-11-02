package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.time.Duration;

@RestController
public class ImageController {

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
                .body(image.content());
    }
}
