package com.dai2010.rollcall.service;

import com.dai2010.rollcall.AppVersion;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Queries the latest published GitHub release and compares semantic version numbers. */
public final class UpdateService {
    private static final URI LATEST_RELEASE_ENDPOINT = URI.create(
            "https://api.github.com/repos/Dai2010/rollcall/releases/latest");
    private static final String API_VERSION = "2026-03-10";

    private final HttpClient httpClient;
    private final URI endpoint;

    public UpdateService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), LATEST_RELEASE_ENDPOINT);
    }

    UpdateService(HttpClient httpClient, URI endpoint) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
    }

    public ReleaseInfo fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "Dai2010-RollCall/" + AppVersion.current())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 403 || response.statusCode() == 429) {
            throw new IOException("GitHub API 请求过于频繁，请稍后再试");
        }
        if (response.statusCode() == 404) {
            throw new IOException("没有找到可用的正式发布版本");
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub API 返回状态码 " + response.statusCode());
        }
        return parseRelease(response.body());
    }

    static ReleaseInfo parseRelease(String json) throws IOException {
        try {
            GitHubRelease response = new Gson().fromJson(json, GitHubRelease.class);
            if (response == null || response.tagName == null || response.tagName.isBlank()) {
                throw new IOException("GitHub 返回的发布信息缺少版本号");
            }
            String version = normalizeVersion(response.tagName);
            URI pageUri = parseReleasePage(response.htmlUrl);
            String displayName = response.name == null || response.name.isBlank()
                    ? response.tagName.trim()
                    : response.name.trim();
            String notes = response.body == null || response.body.isBlank()
                    ? "本次发布暂未提供更新说明。"
                    : response.body.trim();
            Instant publishedAt = parsePublishedAt(response.publishedAt);
            ReleaseAsset installer = selectInstaller(response.assets, version);
            return new ReleaseInfo(response.tagName.trim(), version, displayName, notes, pageUri, publishedAt,
                    installer);
        } catch (JsonParseException | IllegalArgumentException error) {
            throw new IOException("无法解析 GitHub 返回的发布信息", error);
        }
    }

    public static int compareVersions(String left, String right) {
        List<BigInteger> leftParts = parseVersionParts(left);
        List<BigInteger> rightParts = parseVersionParts(right);
        int length = Math.max(leftParts.size(), rightParts.size());
        for (int index = 0; index < length; index++) {
            BigInteger leftPart = index < leftParts.size() ? leftParts.get(index) : BigInteger.ZERO;
            BigInteger rightPart = index < rightParts.size() ? rightParts.get(index) : BigInteger.ZERO;
            int comparison = leftPart.compareTo(rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    public static boolean isUpdateAvailable(String currentVersion, String latestVersion) {
        return compareVersions(latestVersion, currentVersion) > 0;
    }

    static String normalizeVersion(String value) {
        String version = value == null ? "" : value.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        int metadata = version.indexOf('+');
        if (metadata >= 0) {
            version = version.substring(0, metadata);
        }
        int prerelease = version.indexOf('-');
        if (prerelease >= 0) {
            version = version.substring(0, prerelease);
        }
        parseVersionParts(version);
        return version;
    }

    private static List<BigInteger> parseVersionParts(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int suffix = normalized.indexOf('-');
        if (suffix < 0) {
            suffix = normalized.indexOf('+');
        }
        if (suffix >= 0) {
            normalized = normalized.substring(0, suffix);
        }
        if (!normalized.matches("\\d+(?:\\.\\d+)*")) {
            throw new IllegalArgumentException("无效版本号：" + value);
        }
        List<BigInteger> parts = new ArrayList<>();
        for (String part : normalized.split("\\.")) {
            parts.add(new BigInteger(part));
        }
        return parts;
    }

    private static URI parseReleasePage(String value) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("GitHub 返回的发布信息缺少页面地址");
        }
        URI uri = URI.create(value.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new IOException("GitHub 返回了不安全的发布页面地址");
        }
        return uri;
    }

    private static Instant parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static ReleaseAsset selectInstaller(List<GitHubAsset> assets, String version) {
        if (assets == null) {
            return null;
        }
        String expectedExe = "rollcall-" + version.toLowerCase(Locale.ROOT) + ".exe";
        String expectedMsi = "rollcall-" + version.toLowerCase(Locale.ROOT) + ".msi";
        return assets.stream()
                .filter(asset -> asset != null && "uploaded".equalsIgnoreCase(asset.state))
                .filter(asset -> asset.name != null)
                .filter(asset -> {
                    String name = asset.name.toLowerCase(Locale.ROOT);
                    return name.equals(expectedExe) || name.equals(expectedMsi);
                })
                .sorted(Comparator.comparingInt(asset -> asset.name.toLowerCase(Locale.ROOT).endsWith(".exe")
                        ? 0 : 1))
                .map(UpdateService::toReleaseAsset)
                .filter(asset -> asset != null)
                .findFirst()
                .orElse(null);
    }

    private static ReleaseAsset toReleaseAsset(GitHubAsset asset) {
        try {
            if (asset.browserDownloadUrl == null || asset.size <= 0 || asset.digest == null
                    || !asset.digest.toLowerCase(Locale.ROOT).matches("sha256:[0-9a-f]{64}")) {
                return null;
            }
            URI downloadUri = URI.create(asset.browserDownloadUrl);
            String path = downloadUri.getPath() == null ? "" : downloadUri.getPath().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(downloadUri.getScheme())
                    || !"github.com".equalsIgnoreCase(downloadUri.getHost())
                    || !path.startsWith("/dai2010/rollcall/releases/download/")) {
                return null;
            }
            return new ReleaseAsset(asset.name, downloadUri, asset.size,
                    asset.digest.substring("sha256:".length()).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    public record ReleaseInfo(String tagName, String version, String displayName, String notes,
                              URI pageUri, Instant publishedAt, ReleaseAsset installer) {
    }

    public record ReleaseAsset(String name, URI downloadUri, long size, String sha256) {
    }

    private static final class GitHubRelease {
        @SerializedName("tag_name")
        private String tagName;
        private String name;
        private String body;
        @SerializedName("html_url")
        private String htmlUrl;
        @SerializedName("published_at")
        private String publishedAt;
        private List<GitHubAsset> assets;
    }

    private static final class GitHubAsset {
        private String name;
        private long size;
        private String digest;
        private String state;
        @SerializedName("browser_download_url")
        private String browserDownloadUrl;
    }
}
