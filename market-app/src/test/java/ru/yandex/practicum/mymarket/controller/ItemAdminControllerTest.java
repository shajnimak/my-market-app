package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.NewItem;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemAdminControllerTest extends AbstractControllerTest {

    private static final byte[] PNG_CONTENT = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};

    @Test
    void getNewItemForm_shouldRenderForm() {
        String html = getHtml("/admin/items/new");

        assertThat(html).contains("action=\"/admin/items\"", "enctype=\"multipart/form-data\"", "name=\"title\"")
                .doesNotContain("is-invalid");
    }

    @Test
    void createItem_shouldSaveItemAndRedirectToItemPage() {
        when(itemService.createItem(any())).thenReturn(Mono.just(21L));

        webTestClient.post().uri("/admin/items")
                .body(BodyInserters.fromMultipartData(form(" Мяч ", "Футбольный", "2500", PNG_CONTENT, MediaType.IMAGE_PNG).build()))
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().location("/items/21");

        ArgumentCaptor<NewItem> captor = ArgumentCaptor.forClass(NewItem.class);
        verify(itemService).createItem(captor.capture());
        NewItem newItem = captor.getValue();
        assertThat(newItem.title()).isEqualTo("Мяч");
        assertThat(newItem.description()).isEqualTo("Футбольный");
        assertThat(newItem.price()).isEqualTo(2500);
        assertThat(newItem.image().contentType()).isEqualTo("image/png");
        assertThat(newItem.image().content()).isEqualTo(PNG_CONTENT);
    }

    @Test
    void createItem_shouldRenderFormWithErrors_whenFieldsInvalid() {
        webTestClient.post().uri("/admin/items")
                .body(BodyInserters.fromMultipartData(form("", "Футбольный", "-1", PNG_CONTENT, MediaType.IMAGE_PNG).build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(html -> assertThat(occurrences(html, "is-invalid")).isEqualTo(2));

        verify(itemService, never()).createItem(any());
    }

    @Test
    void createItem_shouldTakeContentTypeFromSignature_notFromClient() {
        when(itemService.createItem(any())).thenReturn(Mono.just(21L));

        webTestClient.post().uri("/admin/items")
                .body(BodyInserters.fromMultipartData(
                        form("Мяч", "Футбольный", "2500", PNG_CONTENT, MediaType.APPLICATION_OCTET_STREAM).build()))
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().location("/items/21");

        ArgumentCaptor<NewItem> captor = ArgumentCaptor.forClass(NewItem.class);
        verify(itemService).createItem(captor.capture());
        assertThat(captor.getValue().image().contentType()).isEqualTo("image/png");
    }

    @Test
    void createItem_shouldRejectSvgEvenWithImageContentType() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        webTestClient.post().uri("/admin/items")
                .body(BodyInserters.fromMultipartData(
                        form("Мяч", "Футбольный", "2500", svg, MediaType.valueOf("image/svg+xml")).build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains(ItemAdminController.UNSUPPORTED_FORMAT_MESSAGE));

        verify(itemService, never()).createItem(any());
    }

    @Test
    void createItem_shouldRejectNonImageFile() {
        webTestClient.post().uri("/admin/items")
                .body(BodyInserters.fromMultipartData(
                        form("Мяч", "Футбольный", "2500", new byte[]{1}, MediaType.IMAGE_PNG).build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains(ItemAdminController.UNSUPPORTED_FORMAT_MESSAGE));

        verify(itemService, never()).createItem(any());
    }

    @Test
    void createItem_shouldRequireImage() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("title", "Мяч");
        builder.part("description", "Футбольный");
        builder.part("price", "2500");

        webTestClient.post().uri("/admin/items")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Выберите изображение товара"));

        verify(itemService, never()).createItem(any());
    }

    private static MultipartBodyBuilder form(String title, String description, String price,
                                             byte[] image, MediaType imageType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("title", title);
        builder.part("description", description);
        builder.part("price", price);
        builder.part("image", image).filename("upload").contentType(imageType);
        return builder;
    }
}
