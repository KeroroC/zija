package com.zija.file.internal;

import com.zija.file.exception.FileMediaTypeUnsupportedException;
import com.zija.file.exception.FileSignatureMismatchException;
import com.zija.file.exception.FileTooLargeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileContentInspectorTest {

    private final FileContentInspector inspector = new FileContentInspector();

    @Test
    void detectsJpegSignature() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        var result = inspector.inspect(jpeg, "photo.jpg", "image/jpeg");
        assertThat(result.detectedMediaType()).isEqualTo("image/jpeg");
        assertThat(result.sanitizedBasename()).isEqualTo("photo.jpg");
    }

    @Test
    void detectsPngSignature() {
        byte[] png = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        var result = inspector.inspect(png, "image.png", null);
        assertThat(result.detectedMediaType()).isEqualTo("image/png");
    }

    @Test
    void detectsWebpSignature() {
        byte[] webp = new byte[]{
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50, 0x00, 0x00, 0x00, 0x00
        };
        var result = inspector.inspect(webp, "pic.webp", "image/webp");
        assertThat(result.detectedMediaType()).isEqualTo("image/webp");
    }

    @Test
    void rejectsUnsupportedMediaType() {
        byte[] gif = new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00};
        assertThatThrownBy(() -> inspector.inspect(gif, "anim.gif", null))
                .isInstanceOf(FileMediaTypeUnsupportedException.class);
    }

    @Test
    void rejectsSignatureMismatch() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        assertThatThrownBy(() -> inspector.inspect(jpeg, "photo.png", "image/png"))
                .isInstanceOf(FileSignatureMismatchException.class);
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> inspector.inspect(new byte[0], "empty.jpg", "image/jpeg"))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void rejectsContentExceeding5MiB() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        assertThatThrownBy(() -> inspector.inspect(oversized, "big.jpg", "image/jpeg"))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void sanitizesBasename() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        var result = inspector.inspect(jpeg, "../../../etc/passwd.jpg", "image/jpeg");
        assertThat(result.sanitizedBasename()).doesNotContain("..").doesNotContain("/");
    }

    @Test
    void stripsControlCharacters() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        var result = inspector.inspect(jpeg, "photo\u0000.jpg", "image/jpeg");
        assertThat(result.sanitizedBasename()).doesNotContain("\u0000");
    }

    @Test
    void stripsQuotesFromBasename() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        var result = inspector.inspect(jpeg, "a\".jpg", "image/jpeg");
        assertThat(result.sanitizedBasename()).isEqualTo("a.jpg");
    }

    @Test
    void computesSha256() {
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
        var result = inspector.inspect(content, "test.jpg", "image/jpeg");
        assertThat(result.sha256()).hasSize(64).matches("[0-9a-f]+");
    }
}
