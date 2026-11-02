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
import ru.yandex.practicum.mymarket.service.ItemService;

import java.io.IOException;

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
    public String createItem(@Valid @ModelAttribute("form") NewItemForm form,
                             BindingResult bindingResult) throws IOException {
        MultipartFile image = form.getImage();
        if (image == null || image.isEmpty()) {
            bindingResult.rejectValue("image", "image.empty", "Выберите изображение товара");
        } else if (image.getContentType() == null || !image.getContentType().startsWith("image/")) {
            bindingResult.rejectValue("image", "image.type", "Файл должен быть изображением");
        }
        if (bindingResult.hasErrors()) {
            return "new-item";
        }

        long itemId = itemService.createItem(new NewItem(
                form.getTitle().strip(),
                form.getDescription().strip(),
                form.getPrice(),
                new ImageData(image.getContentType(), image.getBytes())
        ));
        return "redirect:/items/" + itemId;
    }
}
