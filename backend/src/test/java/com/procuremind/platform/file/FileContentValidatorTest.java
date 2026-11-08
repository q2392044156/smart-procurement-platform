package com.procuremind.platform.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileContentValidatorTest {
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.ISO_8859_1); }
    private static byte[] prefix(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    @Test
    void acceptsPdfAndOoxmlAndOle2Headers() {
        assertThat(FileContentValidator.matches("application/pdf", bytes("%PDF-1.7"))).isTrue();
        assertThat(FileContentValidator.matches(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", prefix(0x50, 0x4B, 0x03, 0x04))).isTrue();
        assertThat(FileContentValidator.matches("application/msword", prefix(0xD0, 0xCF, 0x11, 0xE0))).isTrue();
        assertThat(FileContentValidator.matches("image/jpeg", prefix(0xFF, 0xD8, 0xFF))).isTrue();
        assertThat(FileContentValidator.matches("image/png", prefix(0x89, 0x50, 0x4E, 0x47))).isTrue();
    }

    @Test
    void rejectsDeclaredTypeThatDoesNotMatchContent() {
        assertThat(FileContentValidator.matches("application/pdf", prefix(0x50, 0x4B, 0x03, 0x04))).isFalse();
        assertThat(FileContentValidator.matches("image/png", bytes("%PDF-1.7"))).isFalse();
        assertThat(FileContentValidator.matches("application/x-msdownload", prefix(0x4D, 0x5A))).isFalse();
        assertThat(FileContentValidator.matches("application/pdf", new byte[0])).isFalse();
    }

    @Test
    void allowsPlainTextTypesWithoutMagicNumber() {
        assertThat(FileContentValidator.matches("text/plain", bytes("hello"))).isTrue();
        assertThat(FileContentValidator.matches("text/markdown", bytes("# title"))).isTrue();
        assertThat(FileContentValidator.matches("application/octet-stream", bytes("bin"))).isTrue();
    }
}
