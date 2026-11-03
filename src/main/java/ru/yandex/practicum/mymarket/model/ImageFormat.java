package ru.yandex.practicum.mymarket.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Белый список растровых форматов, которые можно загрузить через форму добавления товара.
 * Формат определяется по сигнатуре первых байтов файла, а не по имени и Content-Type от клиента.
 * SVG сюда намеренно не входит: это документ, который может содержать скрипты.
 */
public enum ImageFormat {

    PNG("image/png", new Signature(0, 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n')),
    JPEG("image/jpeg", new Signature(0, 0xFF, 0xD8, 0xFF)),
    GIF("image/gif", new Signature(0, 'G', 'I', 'F', '8')),
    WEBP("image/webp", new Signature(0, 'R', 'I', 'F', 'F'), new Signature(8, 'W', 'E', 'B', 'P'));

    private final String contentType;
    private final Signature[] signatures;

    ImageFormat(String contentType, Signature... signatures) {
        this.contentType = contentType;
        this.signatures = signatures;
    }

    public String getContentType() {
        return contentType;
    }

    public static Optional<ImageFormat> detect(byte[] content) {
        return Arrays.stream(values())
                .filter(format -> format.matches(content))
                .findFirst();
    }

    private boolean matches(byte[] content) {
        return Arrays.stream(signatures).allMatch(signature -> signature.matches(content));
    }

    private record Signature(int offset, int... bytes) {

        boolean matches(byte[] content) {
            if (content.length < offset + bytes.length) {
                return false;
            }
            for (int i = 0; i < bytes.length; i++) {
                if (content[offset + i] != (byte) bytes[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
