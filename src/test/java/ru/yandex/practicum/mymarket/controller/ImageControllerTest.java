package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.exception.ImageNotFoundException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageControllerTest extends AbstractControllerTest {

    @Test
    void getImage_shouldReturnImageBytesWithContentType() throws Exception {
        byte[] content = {1, 2, 3};
        when(itemService.getImage(1)).thenReturn(new ImageData("image/png", content));

        mockMvc.perform(get("/images/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(content))
                .andExpect(header().string("Cache-Control", "max-age=3600"));
    }

    @Test
    void getImage_shouldReturnNotFound_whenImageMissing() throws Exception {
        when(itemService.getImage(2)).thenThrow(new ImageNotFoundException(2));

        mockMvc.perform(get("/images/2"))
                .andExpect(status().isNotFound());
    }
}
