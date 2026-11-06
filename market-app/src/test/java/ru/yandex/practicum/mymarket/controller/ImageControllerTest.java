package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.exception.ImageNotFoundException;

import java.time.Duration;

import static org.mockito.Mockito.when;

class ImageControllerTest extends AbstractControllerTest {

    @Test
    void getImage_shouldReturnImageBytesWithContentType() {
        when(itemService.getImage(1)).thenReturn(Mono.just(new ImageData("image/png", new byte[]{1, 2, 3})));

        webTestClient.get().uri("/images/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG)
                .expectHeader().cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .expectBody(byte[].class).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void getImage_shouldForbidActiveContent_soSvgCanNotRunScripts() {
        when(itemService.getImage(1)).thenReturn(Mono.just(new ImageData("image/svg+xml", "<svg/>".getBytes())));

        webTestClient.get().uri("/images/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Disposition", "attachment")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Content-Security-Policy", ImageController.CONTENT_SECURITY_POLICY);
    }

    @Test
    void getImage_shouldReturnNotFound_whenImageMissing() {
        when(itemService.getImage(1)).thenReturn(Mono.error(new ImageNotFoundException(1)));

        webTestClient.get().uri("/images/1")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getImage_shouldBeAvailable_forAnonymous() {
        when(itemService.getImage(1)).thenReturn(Mono.just(new ImageData("image/png", new byte[]{1})));

        anonymousClient.get().uri("/images/1")
                .exchange()
                .expectStatus().isOk();
    }
}
