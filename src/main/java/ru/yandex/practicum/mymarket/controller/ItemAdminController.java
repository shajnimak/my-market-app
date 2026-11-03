package ru.yandex.practicum.mymarket.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.dto.NewItemForm;
import ru.yandex.practicum.mymarket.model.ImageFormat;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.util.Optional;

/**
 * Добавление товаров на витрину через веб-форму.
 * Формат картинки проверяется по сигнатуре байтов, в базу сохраняется определённый сервером Content-Type.
 */
@Controller
@RequestMapping("/admin/items")
public class ItemAdminController {

    static final String UNSUPPORTED_FORMAT_MESSAGE = "Поддерживаются только изображения PNG, JPEG, GIF и WebP";

    private final ItemService itemService;

    public ItemAdminController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/new")
    public String getNewItemForm(@ModelAttribute("form") NewItemForm form) {
        return "new-item";
    }

    @PostMapping
    public Mono<String> createItem(@Valid @ModelAttribute("form") NewItemForm form,
                                   BindingResult bindingResult) {
        FilePart image = form.getImage();
        Mono<byte[]> content = image == null ? Mono.just(new byte[0]) : readBytes(image);
        return content.flatMap(bytes -> {
            Optional<ImageFormat> format = ImageFormat.detect(bytes);
            if (bytes.length == 0) {
                bindingResult.rejectValue("image", "image.empty", "Выберите изображение товара");
            } else if (format.isEmpty()) {
                bindingResult.rejectValue("image", "image.format", UNSUPPORTED_FORMAT_MESSAGE);
            }
            if (bindingResult.hasErrors()) {
                return Mono.just("new-item");
            }
            return itemService.createItem(new NewItem(
                            form.getTitle().strip(),
                            form.getDescription().strip(),
                            form.getPrice(),
                            new ImageData(format.get().getContentType(), bytes)
                    ))
                    .map(itemId -> "redirect:/items/" + itemId);
        });
    }

    private static Mono<byte[]> readBytes(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0]);
    }
}
