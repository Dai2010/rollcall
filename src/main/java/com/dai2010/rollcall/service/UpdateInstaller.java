package com.dai2010.rollcall.service;

import com.dai2010.rollcall.AppVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Downloads a verified Windows installer through ghfast.top and launches it. */
public final class UpdateInstaller {
    private static final String PROXY_PREFIX = "https://ghfast.top/";
    private static final long MAX_INSTALLER_SIZE = 1024L * 1024L * 1024L;

    private final HttpClient httpClient;

    public UpdateInstaller() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    UpdateInstaller(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Path download(UpdateService.ReleaseAsset asset, IntConsumer progress)
            throws IOException, InterruptedException {
        validateAsset(asset);
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "rollcall-updates");
        Files.createDirectories(directory);
        Path target = directory.resolve(asset.name());
        Path partial = directory.resolve(asset.name() + ".part");

        if (Files.isRegularFile(target) && matchesDigest(target, asset.sha256())) {
            progress.accept(100);
            return target;
        }

        Files.deleteIfExists(partial);
        try {
            HttpRequest request = HttpRequest.newBuilder(proxyUri(asset.downloadUri()))
                    .timeout(Duration.ofMinutes(10))
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", "Dai2010-RollCall/" + AppVersion.current())
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("代理下载返回状态码 " + response.statusCode());
            }
            copyAndVerify(response.body(), partial, asset, progress);
            moveIntoPlace(partial, target);
            progress.accept(100);
            return target;
        } catch (IOException | InterruptedException | RuntimeException error) {
            Files.deleteIfExists(partial);
            throw error;
        }
    }

    public void launch(Path installer) throws IOException {
        if (!isWindows()) {
            throw new IOException("自动安装目前仅支持 Windows");
        }
        String name = installer.getFileName().toString().toLowerCase(Locale.ROOT);
        ProcessBuilder process;
        if (name.endsWith(".exe")) {
            process = new ProcessBuilder(installer.toAbsolutePath().toString());
        } else if (name.endsWith(".msi")) {
            process = new ProcessBuilder("msiexec.exe", "/i", installer.toAbsolutePath().toString());
        } else {
            throw new IOException("不支持的安装包格式");
        }
        process.directory(installer.toAbsolutePath().getParent().toFile());
        process.start();
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    static URI proxyUri(URI original) {
        if (original == null || !"https".equalsIgnoreCase(original.getScheme())
                || !"github.com".equalsIgnoreCase(original.getHost())) {
            throw new IllegalArgumentException("只能代理 GitHub HTTPS 下载地址");
        }
        return URI.create(PROXY_PREFIX + original);
    }

    private static void validateAsset(UpdateService.ReleaseAsset asset) throws IOException {
        if (asset == null || asset.name() == null
                || !asset.name().matches("[A-Za-z0-9._-]+")) {
            throw new IOException("发布资源名称无效");
        }
        String name = asset.name().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".exe") && !name.endsWith(".msi")) {
            throw new IOException("发布资源不是 Windows 安装包");
        }
        if (asset.size() <= 0 || asset.size() > MAX_INSTALLER_SIZE || asset.sha256() == null
                || !asset.sha256().matches("[0-9a-fA-F]{64}")) {
            throw new IOException("发布资源缺少有效的 SHA-256 校验信息");
        }
        try {
            proxyUri(asset.downloadUri());
        } catch (IllegalArgumentException error) {
            throw new IOException(error.getMessage(), error);
        }
    }

    static void copyAndVerify(InputStream responseBody, Path partial, UpdateService.ReleaseAsset asset,
                              IntConsumer progress) throws IOException {
        MessageDigest digest = sha256Digest();
        long bytesWritten = 0;
        try (InputStream input = responseBody;
             OutputStream output = Files.newOutputStream(partial, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                bytesWritten += read;
                if (bytesWritten > asset.size()) {
                    throw new IOException("安装包大小校验失败");
                }
                int percent = (int) Math.min(99, (double) bytesWritten * 100 / asset.size());
                progress.accept(percent);
            }
        }
        if (bytesWritten != asset.size()) {
            throw new IOException("安装包大小校验失败");
        }
        String actualDigest = HexFormat.of().formatHex(digest.digest());
        if (!MessageDigest.isEqual(actualDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                asset.sha256().toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("安装包 SHA-256 校验失败，已拒绝启动");
        }
    }

    private static boolean matchesDigest(Path file, String expected) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        return actual.equalsIgnoreCase(expected);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
