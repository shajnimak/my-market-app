package ru.yandex.practicum.mymarket.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
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
import ru.yandex.practicum.mymarket.service.ItemService;

/**
 * Добавление товаров на витрину через веб-форму.
 */
@Controller
@RequestMapping("/admin/items")
public class ItemAdminController {

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
        MediaType contentType = image == null ? null : image.headers().getContentType();
        if (image == null || image.filename().isBlank()) {
            bindingResult.rejectValue("image", "image.empty", "Выберите изображение товара");
        } else if (contentType == null || !"image".equals(contentType.getType())) {
            bindingResult.rejectValue("image", "image.type", "Файл должен быть изображением");
        }
        if (bindingResult.hasErrors()) {
            return Mono.just("new-item");
        }

        return readBytes(image)
                .flatMap(content -> itemService.createItem(new NewItem(
                        form.getTitle().strip(),
                        form.getDescription().strip(),
                        form.getPrice(),
                        new ImageData(contentType.toString(), content)
                )))
                .map(itemId -> "redirect:/items/" + itemId);
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
