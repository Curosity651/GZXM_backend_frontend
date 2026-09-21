package com.gzxm.server.modules.file.infrastructure;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.config.AppProperties;
import com.gzxm.server.modules.file.application.FileStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "app.file", name = "provider", havingValue = "WEBDAV")
public class WebdavFileStorage implements FileStorage {
    private static final Pattern CONTENT_LENGTH_XML =
            Pattern.compile("getcontentlength[^>]*>\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private final HttpClient client;
    private final String base;
    private final String authorization;
    private final Duration requestTimeout;

    public WebdavFileStorage(AppProperties properties) {
        AppProperties.File.Webdav webdav = properties.file().webdav();
        if (webdav == null || webdav.baseUrl() == null || webdav.baseUrl().isBlank())
            throw new IllegalArgumentException("WEBDAV 文件存储缺少 app.file.webdav.base-url 配置");
        if (webdav.username() == null || webdav.username().isBlank()
                || webdav.password() == null || webdav.password().isBlank())
            throw new IllegalArgumentException("WEBDAV 文件存储缺少 app.file.webdav.username/password 配置");
        this.base = webdav.baseUrl().endsWith("/") ? webdav.baseUrl() : webdav.baseUrl() + "/";
        URI baseUri = URI.create(this.base);
        if (baseUri.getScheme() == null || baseUri.getHost() == null)
            throw new IllegalArgumentException("WEBDAV 文件存储 base-url 不正确: 需包含协议与主机");
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(
                (webdav.username() + ":" + webdav.password()).getBytes(StandardCharsets.UTF_8));
        this.requestTimeout = webdav.requestTimeout() == null ? Duration.ofSeconds(120) : webdav.requestTimeout();
        this.client = HttpClient.newBuilder()
                .connectTimeout(webdav.connectTimeout() == null ? Duration.ofSeconds(5) : webdav.connectTimeout())
                .build();
    }

    @Override public String provider() { return "WEBDAV"; }
    @Override public String createUploadUrl(long fileId, String objectKey, Duration ttl) {
        return "/api/v1/files/" + fileId + "/content";
    }
    @Override public String createDownloadUrl(long fileId, String objectKey, Duration ttl, boolean preview) {
        return "/api/v1/files/" + fileId + "/content?preview=" + preview;
    }

    @Override public boolean exists(String objectKey) {
        URI uri = uri(objectKey);
        try {
            HttpResponse<Void> head = send(request(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding());
            if (head.statusCode() == 404) return false;
            if (head.statusCode() >= 200 && head.statusCode() < 300) return true;
            HttpResponse<Void> propfind = send(request(uri)
                    .method("PROPFIND", HttpRequest.BodyPublishers.noBody())
                    .header("Depth", "0").build(), HttpResponse.BodyHandlers.discarding());
            return propfind.statusCode() == 207;
        } catch (IOException ex) {
            throw unavailable(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable(ex);
        }
    }

    @Override public long size(String objectKey) {
        URI uri = uri(objectKey);
        try {
            HttpResponse<Void> head = send(request(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding());
            if (head.statusCode() == 404)
                throw BusinessException.notFound("FILE_OBJECT_MISSING", "文件内容不存在");
            if (head.statusCode() >= 200 && head.statusCode() < 300)
                return head.headers().firstValueAsLong("Content-Length").orElse(-1);
            HttpResponse<String> propfind = send(request(uri)
                    .method("PROPFIND", HttpRequest.BodyPublishers.noBody())
                    .header("Depth", "0").build(), HttpResponse.BodyHandlers.ofString());
            if (propfind.statusCode() == 404)
                throw BusinessException.notFound("FILE_OBJECT_MISSING", "文件内容不存在");
            if (propfind.statusCode() != 207 || propfind.body() == null)
                throw unavailable(new IOException("WebDAV PROPFIND 返回 " + propfind.statusCode()));
            var matcher = CONTENT_LENGTH_XML.matcher(propfind.body());
            return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
        } catch (IOException ex) {
            throw unavailable(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable(ex);
        }
    }

    @Override public void write(String objectKey, InputStream content, long size) {
        URI target = uri(objectKey);
        URI temporary = uri(objectKey + "." + UUID.randomUUID().toString().replace("-", "") + ".tmp");
        Path staging = null;
        try {
            ensureCollections(parentSegments(objectKey));
            staging = Files.createTempFile("webdav-upload-", ".bin");
            Files.copy(content, staging, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(staging) != size) {
                Files.deleteIfExists(staging);
                throw BusinessException.validation("FILE_SIZE_MISMATCH", "文件大小与上传票据不一致");
            }
            HttpResponse<Void> put = send(request(temporary)
                    .PUT(HttpRequest.BodyPublishers.ofFile(staging)).build(), HttpResponse.BodyHandlers.discarding());
            if (put.statusCode() < 200 || put.statusCode() >= 300)
                throw BusinessException.conflict("FILE_STORAGE_WRITE_FAILED", "文件写入失败");
            HttpResponse<Void> move = send(request(temporary)
                    .method("MOVE", HttpRequest.BodyPublishers.noBody())
                    .header("Destination", target.toString()).header("Overwrite", "T").build(),
                    HttpResponse.BodyHandlers.discarding());
            if (move.statusCode() < 200 || move.statusCode() >= 300) {
                deleteQuietly(temporary);
                throw BusinessException.conflict("FILE_STORAGE_WRITE_FAILED", "文件写入失败");
            }
        } catch (IOException ex) {
            deleteQuietly(temporary);
            throw unavailable(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            deleteQuietly(temporary);
            throw unavailable(ex);
        } finally {
            if (staging != null) try { Files.deleteIfExists(staging); } catch (IOException ignored) { }
        }
    }

    @Override public InputStream read(String objectKey) {
        URI uri = uri(objectKey);
        try {
            HttpResponse<InputStream> response = send(request(uri).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 404) {
                closeQuietly(response.body());
                throw BusinessException.notFound("FILE_OBJECT_MISSING", "文件内容不存在");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                closeQuietly(response.body());
                throw unavailable(new IOException("WebDAV GET 返回 " + response.statusCode()));
            }
            return response.body();
        } catch (IOException ex) {
            throw unavailable(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable(ex);
        }
    }

    @Override public void delete(String objectKey) {
        try {
            HttpResponse<Void> response = send(request(uri(objectKey))
                    .method("DELETE", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 404 && (response.statusCode() < 200 || response.statusCode() >= 300))
                throw unavailable(new IOException("WebDAV DELETE 返回 " + response.statusCode()));
        } catch (IOException ex) {
            throw unavailable(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable(ex);
        }
    }

    private void ensureCollections(List<String> segments) throws IOException, InterruptedException {
        StringBuilder current = new StringBuilder(base);
        for (String segment : segments) {
            current.append(encode(segment)).append('/');
            HttpResponse<Void> response = send(request(URI.create(current.toString()))
                    .method("MKCOL", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 405) continue;
            if (status < 200 || status >= 300) throw new IOException("WebDAV MKCOL 返回 " + status);
        }
    }

    private void deleteQuietly(URI uri) {
        try { send(request(uri).method("DELETE", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()); }
        catch (Exception ignored) { }
    }
    private void closeQuietly(InputStream stream) {
        if (stream == null) return;
        try { stream.close(); } catch (IOException ignored) { }
    }
    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        try { return client.send(request, handler); }
        catch (IOException first) { return client.send(request, handler); }
    }
    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(requestTimeout).header("Authorization", authorization);
    }
    private URI uri(String objectKey) { return URI.create(base + encoded(objectKey)); }
    private List<String> parentSegments(String objectKey) {
        String[] segments = validate(objectKey).split("/");
        List<String> parents = new ArrayList<>(segments.length - 1);
        for (int i = 0; i < segments.length - 1; i++) parents.add(segments[i]);
        return parents;
    }
    private String encoded(String objectKey) {
        StringBuilder path = new StringBuilder();
        for (String segment : validate(objectKey).split("/")) path.append(encode(segment)).append('/');
        path.setLength(path.length() - 1);
        return path.toString();
    }
    private String validate(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")
                || objectKey.contains("\\") || objectKey.contains("%"))
            throw BusinessException.validation("INVALID_OBJECT_KEY", "文件对象键不正确");
        for (String segment : objectKey.split("/"))
            if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
                throw BusinessException.validation("INVALID_OBJECT_KEY", "文件对象键不正确");
        return objectKey;
    }
    private String encode(String segment) {
        StringBuilder out = new StringBuilder(segment.length());
        for (char c : segment.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '.' || c == '_') out.append(c);
            else out.append('%').append(String.format("%02X", (int) c));
        }
        return out.toString();
    }
    private BusinessException unavailable(Exception cause) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "FILE_STORAGE_UNAVAILABLE", "文件存储服务暂不可用");
    }
}
