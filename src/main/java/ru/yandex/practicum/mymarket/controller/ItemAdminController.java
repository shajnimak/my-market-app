package ru.yandex.practicum.mymarket.controller;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.dto.NewItemForm;
import ru.yandex.practicum.mymarket.model.ImageFormat;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.io.IOException;
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
    public String createItem(@Valid @ModelAttribute("form") NewItemForm form,
                             BindingResult bindingResult) throws IOException {
        MultipartFile image = form.getImage();
        byte[] content = image == null ? new byte[0] : image.getBytes();
        Optional<ImageFormat> format = ImageFormat.detect(content);
        if (content.length == 0) {
            bindingResult.rejectValue("image", "image.empty", "Выберите изображение товара");
        } else if (format.isEmpty()) {
            bindingResult.rejectValue("image", "image.format", UNSUPPORTED_FORMAT_MESSAGE);
        }
        if (bindingResult.hasErrors()) {
            return "new-item";
        }

        long itemId = itemService.createItem(new NewItem(
                form.getTitle().strip(),
                form.getDescription().strip(),
                form.getPrice(),
                new ImageData(format.get().getContentType(), content)
        ));
        return "redirect:/items/" + itemId;
    }
}
