package com.manu.frosted.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Talks to the GitHub releases API and downloads / verifies the jar.
 *
 * Trust rules: only the configured "owner/repo" is queried, the asset must live under
 * https://github.com/owner/repo/releases/download/, the file size is capped, the SHA-256 is
 * checked when GitHub provides one, and the jar must really be this mod (id, version, Minecraft
 * version) before anything gets installed.
 */
public final class UpdateClient {
    /** Overridden by tests only. */
    static String apiBase = "https://api.github.com";
    static boolean allowLocalHttp = false;

    private static final long MAX_JAR_BYTES = 30L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    private UpdateClient() {}

    public static boolean validRepo(String repo) {
        return repo != null && repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    }

    /** Newest release (optionally including pre-releases) or null when the repo has none. */
    public static ReleaseInfo fetchLatest(String repo, boolean prereleases) throws IOException, InterruptedException {
        if (!validRepo(repo)) throw new IOException("Repository must look like owner/name");

        String url = apiBase + "/repos/" + repo + (prereleases ? "/releases?per_page=10" : "/releases/latest");
        String body = get(url);

        Object json;
        try {
            json = MiniJson.parse(body);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unreadable answer from the release server", e);
        }

        List<Object> list = MiniJson.array(json);
        if (list != null) {
            for (Object entry : list) {
                Map<String, Object> m = MiniJson.object(entry);
                if (m == null || MiniJson.bool(m, "draft")) continue;
                ReleaseInfo info = parseRelease(m, repo);
                if (info != null) return info;
            }
            return null;
        }
        return parseRelease(MiniJson.object(json), repo);
    }

    static ReleaseInfo parseRelease(Map<String, Object> m, String repo) {
        if (m == null || MiniJson.bool(m, "draft")) return null;
        String tag = MiniJson.string(m, "tag_name");
        if (tag == null || tag.isBlank()) return null;

        List<Object> assets = MiniJson.array(m.get("assets"));
        if (assets == null) return null;

        for (Object o : assets) {
            Map<String, Object> asset = MiniJson.object(o);
            String name = MiniJson.string(asset, "name");
            String url = MiniJson.string(asset, "browser_download_url");
            if (name == null || url == null) continue;

            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".jar") || lower.contains("sources") || lower.contains("-dev")) continue;
            if (!trustedAssetUrl(url, repo)) continue;

            String digest = MiniJson.string(asset, "digest");
            String sha = digest != null && digest.startsWith("sha256:") ? digest.substring(7).toLowerCase(Locale.ROOT) : null;

            String notes = MiniJson.string(m, "body");
            return new ReleaseInfo(tag, Version.normalize(tag), notes == null ? "" : notes, name, url,
                MiniJson.number(asset, "size"), sha, MiniJson.bool(m, "prerelease"));
        }
        return null;
    }

    static boolean trustedAssetUrl(String url, String repo) {
        if (allowLocalHttp && url.startsWith("http://localhost")) return true;
        return url.toLowerCase(Locale.ROOT).startsWith(("https://github.com/" + repo + "/releases/download/").toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- download

    /**
     * Downloads the release jar into dir as frosted-VERSION.jar.update (a name Fabric ignores)
     * and returns its path. Throws if the size or checksum is wrong.
     */
    public static Path download(ReleaseInfo release, Path dir) throws IOException, InterruptedException {
        if (release.assetSize() > MAX_JAR_BYTES) throw new IOException("Release file is unexpectedly large");

        String safe = release.version().replaceAll("[^A-Za-z0-9._-]", "_");
        Path part = dir.resolve("frosted-" + safe + ".jar.part");
        Path target = dir.resolve("frosted-" + safe + ".jar.update");

        HttpRequest req = HttpRequest.newBuilder(URI.create(release.assetUrl()))
            .header("User-Agent", "frosted-updater")
            .timeout(Duration.ofSeconds(60))
            .GET().build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) throw new IOException("Download failed (HTTP " + res.statusCode() + ")");

        MessageDigest digest = sha256();
        long total = 0;
        try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_JAR_BYTES) throw new IOException("Download exceeds the size limit");
                digest.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw e;
        }

        String actual = HexFormat.of().formatHex(digest.digest());
        if (release.sha256() != null && !release.sha256().equals(actual)) {
            Files.deleteIfExists(part);
            throw new IOException("Checksum mismatch, the download was discarded");
        }

        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** Makes sure the downloaded file really is this mod, for this version of the game. */
    public static void verifyJar(Path jar, String expectedVersion, String mcVersion) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) throw new IOException("Not a Fabric mod (no fabric.mod.json)");

            String text;
            try (InputStream in = zip.getInputStream(entry)) {
                text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            Map<String, Object> meta;
            try {
                meta = MiniJson.object(MiniJson.parse(text));
            } catch (IllegalArgumentException e) {
                throw new IOException("fabric.mod.json is unreadable", e);
            }
            if (meta == null) throw new IOException("fabric.mod.json is unreadable");

            if (!"frosted".equals(MiniJson.string(meta, "id"))) throw new IOException("This jar is not Frosted");

            String version = MiniJson.string(meta, "version");
            if (version == null || Version.compare(version, expectedVersion) != 0) {
                throw new IOException("Jar version (" + version + ") does not match the release (" + expectedVersion + ")");
            }

            Map<String, Object> depends = MiniJson.object(meta.get("depends"));
            Object mc = depends == null ? null : depends.get("minecraft");
            if (mcVersion != null && mc instanceof String req && !req.isEmpty() && Character.isDigit(req.charAt(0))
                && !req.equals(mcVersion)) {
                throw new IOException("This release is for Minecraft " + req + ", you run " + mcVersion);
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("Downloaded file is not a valid jar", e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "frosted-updater")
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(15))
            .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) throw new IOException("Repository or release not found (is it public?)");
        if (res.statusCode() == 403) throw new IOException("GitHub refused the request (rate limit?)");
        if (res.statusCode() != 200) throw new IOException("Release server answered HTTP " + res.statusCode());
        return res.body();
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
