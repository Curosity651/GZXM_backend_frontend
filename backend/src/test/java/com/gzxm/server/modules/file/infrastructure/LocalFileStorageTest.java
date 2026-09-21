package com.gzxm.server.modules.file.infrastructure;

import com.gzxm.server.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageTest {
    @TempDir Path temp;

    @Test
    void storesContentUnderConfiguredRootAndReadsSameBytes() throws Exception {
        var storage = storage(temp);
        assertThat(storage.provider()).isEqualTo("FILESYSTEM");
        assertThat(storage.supportsProvider("MOCK")).isTrue();
        byte[] bytes = new byte[] { 0, 1, 2, 3, -1 };
        storage.write("archive/7/document", new java.io.ByteArrayInputStream(bytes), bytes.length);
        assertThat(storage.exists("archive/7/document")).isTrue();
        assertThat(storage.read("archive/7/document").readAllBytes()).isEqualTo(bytes);
        assertThat(temp.resolve("archive/7/document")).hasBinaryContent(bytes);
    }

    @Test
    void createsConfiguredRootWhenItDoesNotExist() {
        Path root = temp.resolve("files");
        var storage = storage(root);
        assertThat(root).isDirectory();
        assertThat(storage.provider()).isEqualTo("FILESYSTEM");
    }

    @Test
    void remainsAvailableForHistoricalFilesWhenWebdavIsActive() {
        var properties = new AppProperties(null, null,
                new AppProperties.File("WEBDAV", temp, Duration.ofMinutes(15), Duration.ofMinutes(10), null, null));
        assertThat(new LocalFileStorage(properties).supportsProvider("FILESYSTEM")).isTrue();
    }

    @Test
    void copiedObjectsRemainReadableAfterChangingStorageRoot() throws Exception {
        Path originalRoot = Files.createDirectory(temp.resolve("local"));
        Path nasRoot = Files.createDirectory(temp.resolve("nas-mount"));
        String key = "archive/7/existing-file";
        byte[] bytes = "existing archive material".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var original = storage(originalRoot);
        original.write(key, new java.io.ByteArrayInputStream(bytes), bytes.length);
        Path destination = nasRoot.resolve(key);
        Files.createDirectories(destination.getParent());
        Files.copy(originalRoot.resolve(key), destination);

        var switched = storage(nasRoot);
        assertThat(switched.supportsProvider("MOCK")).isTrue();
        assertThat(switched.read(key).readAllBytes()).isEqualTo(bytes);
    }

    private LocalFileStorage storage(Path root) {
        return new LocalFileStorage(new AppProperties(null, null,
                new AppProperties.File("FILESYSTEM", root, Duration.ofMinutes(15), Duration.ofMinutes(10), null, null)));
    }
}
