package com.dai2010.rollcall.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateInstallerTest {
    @Test
    void prefixesGitHubReleaseUrlWithGhfast() {
        URI original = URI.create(
                "https://github.com/Dai2010/rollcall/releases/download/v0.0.3/RollCall-0.0.3.exe");

        assertEquals("https://ghfast.top/" + original, UpdateInstaller.proxyUri(original).toString());
    }

    @Test
    void refusesNonGitHubAndInsecureUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateInstaller.proxyUri(URI.create("https://example.com/RollCall.exe")));
        assertThrows(IllegalArgumentException.class,
                () -> UpdateInstaller.proxyUri(URI.create("http://github.com/Dai2010/rollcall/file.exe")));
    }

    @Test
    void acceptsDownloadWithMatchingSizeAndDigest() throws Exception {
        byte[] content = "verified installer content".getBytes(StandardCharsets.UTF_8);
        UpdateService.ReleaseAsset asset = asset(content.length, sha256(content));
        Path partial = Files.createTempFile("rollcall-update", ".part");
        AtomicInteger progress = new AtomicInteger();

        try {
            UpdateInstaller.copyAndVerify(new ByteArrayInputStream(content), partial, asset, progress::set);

            assertArrayEquals(content, Files.readAllBytes(partial));
            assertEquals(99, progress.get());
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    @Test
    void rejectsDownloadWithWrongSizeOrDigest() throws Exception {
        byte[] content = "unexpected content".getBytes(StandardCharsets.UTF_8);
        Path wrongSize = Files.createTempFile("rollcall-update-size", ".part");
        Path wrongDigest = Files.createTempFile("rollcall-update-digest", ".part");

        try {
            assertThrows(IOException.class, () -> UpdateInstaller.copyAndVerify(
                    new ByteArrayInputStream(content), wrongSize, asset(content.length - 1, sha256(content)),
                    progress -> { }));
            assertThrows(IOException.class, () -> UpdateInstaller.copyAndVerify(
                    new ByteArrayInputStream(content), wrongDigest, asset(content.length, "0".repeat(64)),
                    progress -> { }));
        } finally {
            Files.deleteIfExists(wrongSize);
            Files.deleteIfExists(wrongDigest);
        }
    }

    private UpdateService.ReleaseAsset asset(long size, String digest) {
        return new UpdateService.ReleaseAsset("RollCall-0.0.3.exe", URI.create(
                "https://github.com/Dai2010/rollcall/releases/download/v0.0.3/RollCall-0.0.3.exe"),
                size, digest);
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
