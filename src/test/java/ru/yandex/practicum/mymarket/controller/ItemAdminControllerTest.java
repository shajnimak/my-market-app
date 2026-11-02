package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import ru.yandex.practicum.mymarket.dto.NewItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ItemAdminControllerTest extends AbstractControllerTest {

    private static final MockMultipartFile IMAGE =
            new MockMultipartFile("image", "ball.png", "image/png", new byte[]{1, 2, 3});

    @Test
    void getNewItemForm_shouldRenderForm() throws Exception {
        mockMvc.perform(get("/admin/items/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("new-item"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    void createItem_shouldSaveItemAndRedirectToItemPage() throws Exception {
        when(itemService.createItem(any())).thenReturn(21L);

        mockMvc.perform(multipart("/admin/items")
                        .file(IMAGE)
                        .param("title", " Мяч ")
                        .param("description", "Футбольный")
                        .param("price", "2500"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/items/21"));

        ArgumentCaptor<NewItem> captor = ArgumentCaptor.forClass(NewItem.class);
        verify(itemService).createItem(captor.capture());
        NewItem newItem = captor.getValue();
        assertThat(newItem.title()).isEqualTo("Мяч");
        assertThat(newItem.description()).isEqualTo("Футбольный");
        assertThat(newItem.price()).isEqualTo(2500);
        assertThat(newItem.image().contentType()).isEqualTo("image/png");
        assertThat(newItem.image().content()).containsExactly(1, 2, 3);
    }

    @Test
    void createItem_shouldRenderFormWithErrors_whenFieldsInvalid() throws Exception {
        mockMvc.perform(multipart("/admin/items")
                        .file(IMAGE)
                        .param("title", "")
                        .param("description", "Футбольный")
                        .param("price", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("new-item"))
                .andExpect(model().attributeHasFieldErrors("form", "title", "price"));

        verify(itemService, never()).createItem(any());
    }

    @Test
    void createItem_shouldRejectNonImageFile() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile("image", "notes.txt", "text/plain", new byte[]{1});

        mockMvc.perform(multipart("/admin/items")
                        .file(textFile)
                        .param("title", "Мяч")
                        .param("description", "Футбольный")
                        .param("price", "2500"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "image"));

        verify(itemService, never()).createItem(any());
    }

    @Test
    void createItem_shouldRequireImage() throws Exception {
        mockMvc.perform(multipart("/admin/items")
                        .param("title", "Мяч")
                        .param("description", "Футбольный")
                        .param("price", "2500"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "image"));

        verify(itemService, never()).createItem(any());
    }
}
