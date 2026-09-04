package com.dai2010.rollcall.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {
    @Test
    void parsesLatestReleaseInformation() throws Exception {
        String json = """
                {
                  "tag_name": "v0.0.3",
                  "name": "点名助手 v0.0.3",
                  "body": "新增自动更新检查。",
                  "html_url": "https://github.com/Dai2010/rollcall/releases/tag/v0.0.3",
                  "published_at": "2026-09-05T08:30:00Z",
                  "assets": [
                    {
                      "name": "RollCall-0.0.3.msi",
                      "size": 50123456,
                      "digest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                      "state": "uploaded",
                      "browser_download_url": "https://github.com/Dai2010/rollcall/releases/download/v0.0.3/RollCall-0.0.3.msi"
                    },
                    {
                      "name": "RollCall-0.0.3.exe",
                      "size": 51123456,
                      "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "state": "uploaded",
                      "browser_download_url": "https://github.com/Dai2010/rollcall/releases/download/v0.0.3/RollCall-0.0.3.exe"
                    }
                  ]
                }
                """;

        UpdateService.ReleaseInfo release = UpdateService.parseRelease(json);

        assertEquals("v0.0.3", release.tagName());
        assertEquals("0.0.3", release.version());
        assertEquals("点名助手 v0.0.3", release.displayName());
        assertEquals("新增自动更新检查。", release.notes());
        assertEquals("github.com", release.pageUri().getHost());
        assertEquals("RollCall-0.0.3.exe", release.installer().name());
        assertEquals(51_123_456, release.installer().size());
        assertEquals("a".repeat(64), release.installer().sha256());
    }

    @Test
    void comparesNumericVersionComponents() {
        assertTrue(UpdateService.isUpdateAvailable("0.0.3", "v0.0.4"));
        assertTrue(UpdateService.compareVersions("v1.0.0", "0.12.9") > 0);
        assertEquals(0, UpdateService.compareVersions("0.0.3", "0.0.3.0"));
        assertFalse(UpdateService.isUpdateAvailable("0.0.3", "v0.0.2"));
    }

    @Test
    void rejectsIncompleteOrUnsafeReleaseInformation() {
        assertThrows(IOException.class, () -> UpdateService.parseRelease("{}"));
        assertThrows(IOException.class, () -> UpdateService.parseRelease("""
                {
                  "tag_name": "v0.0.3",
                  "html_url": "http://example.com/download"
                }
                """));
        assertThrows(IllegalArgumentException.class,
                () -> UpdateService.compareVersions("not-a-version", "0.0.3"));
    }

    @Test
    void ignoresInstallerFromUnexpectedDownloadHost() throws Exception {
        UpdateService.ReleaseInfo release = UpdateService.parseRelease("""
                {
                  "tag_name": "v0.0.3",
                  "html_url": "https://github.com/Dai2010/rollcall/releases/tag/v0.0.3",
                  "assets": [{
                    "name": "RollCall-0.0.3.exe",
                    "size": 100,
                    "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "state": "uploaded",
                    "browser_download_url": "https://example.com/RollCall-0.0.3.exe"
                  }]
                }
                """);

        assertNull(release.installer());
    }
}
