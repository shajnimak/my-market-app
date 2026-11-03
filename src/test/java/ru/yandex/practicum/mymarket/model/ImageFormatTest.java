package ru.yandex.practicum.mymarket.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFormatTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    static Stream<Arguments> supportedImages() {
        return Stream.of(
                Arguments.of(PNG, ImageFormat.PNG),
                Arguments.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16}, ImageFormat.JPEG),
                Arguments.of("GIF89a....".getBytes(StandardCharsets.US_ASCII), ImageFormat.GIF),
                Arguments.of("RIFF\u0024\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1), ImageFormat.WEBP)
        );
    }

    @ParameterizedTest
    @MethodSource("supportedImages")
    void detect_shouldRecognizeFormatBySignature(byte[] content, ImageFormat expected) {
        assertThat(ImageFormat.detect(content)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
            "<?xml version=\"1.0\"?><svg/>",
            "<html><script>alert(1)</script></html>",
            "RIFF\u0024\0\0\0WAVEfmt ",
            "PNG",
            ""
    })
    void detect_shouldRejectUnsupportedContent(String content) {
        assertThat(ImageFormat.detect(content.getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
    }
}
