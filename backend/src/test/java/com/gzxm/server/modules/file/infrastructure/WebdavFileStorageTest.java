package com.gzxm.server.modules.file.infrastructure;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebdavFileStorageTest {
    private HttpServer server;
    private WebdavFileStorage storage;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final Set<String> collections = ConcurrentHashMap.newKeySet();

    @BeforeEach void setUp() throws Exception {
        collections.add("/webdata/");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            byte[] body = exchange.getRequestBody().readAllBytes();
            int status;
            switch (method) {
                case "MKCOL" -> status = collections.add(path) ? 201 : 405;
                case "PUT" -> { objects.put(path, body); status = 201; }
                case "MOVE" -> {
                    String destination = exchange.getRequestHeaders().getFirst("Destination");
                    String destinationPath = destination == null ? null : URI.create(destination).getPath();
                    byte[] moved = objects.remove(path);
                    if (moved == null || destinationPath == null) status = 404;
                    else { objects.put(destinationPath, moved); status = 201; }
                }
                case "GET" -> {
                    byte[] found = objects.get(path);
                    if (found == null) status = 404;
                    else {
                        exchange.sendResponseHeaders(200, found.length);
                        exchange.getResponseBody().write(found);
                        exchange.close(); return;
                    }
                }
                case "HEAD" -> {
                    byte[] found = objects.get(path);
                    if (found == null) status = 404;
                    else { exchange.getResponseHeaders().set("Content-Length", String.valueOf(found.length)); status = 200; }
                }
                case "DELETE" -> { objects.remove(path); status = 204; }
                default -> status = 405;
            }
            exchange.sendResponseHeaders(status, -1); exchange.close();
        });
        server.start();
        storage = new WebdavFileStorage(properties(server.getAddress().getPort()));
    }

    @AfterEach void tearDown() { if (server != null) server.stop(0); }

    @Test void writesAtomicallyReadsAndDeletes() throws Exception {
        byte[] bytes = "archive material".getBytes(StandardCharsets.UTF_8);
        storage.write("archive/7/document", new java.io.ByteArrayInputStream(bytes), bytes.length);
        assertThat(storage.exists("archive/7/document")).isTrue();
        assertThat(storage.size("archive/7/document")).isEqualTo(bytes.length);
        try (var stream = storage.read("archive/7/document")) { assertThat(stream.readAllBytes()).isEqualTo(bytes); }
        assertThat(objects.keySet()).noneMatch(key -> key.endsWith(".tmp"));
        storage.delete("archive/7/document");
        assertThat(storage.exists("archive/7/document")).isFalse();
    }

    @Test void rejectsTraversalObjectKeys() {
        assertThatThrownBy(() -> storage.read("../escape"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("INVALID_OBJECT_KEY");
    }

    private AppProperties properties(int port) {
        return new AppProperties(null, null, new AppProperties.File("WEBDAV", Path.of("storage/files"),
                Duration.ofMinutes(15), Duration.ofMinutes(10),
                new AppProperties.File.Webdav("http://127.0.0.1:" + port + "/webdata/", "svc", "secret",
                        Duration.ofSeconds(2), Duration.ofSeconds(10)), null));
    }
}
