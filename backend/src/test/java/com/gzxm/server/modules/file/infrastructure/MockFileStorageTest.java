package com.gzxm.server.modules.file.infrastructure;

import com.gzxm.server.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockFileStorageTest {
    @TempDir Path temp;

    @Test
    void storesContentUnderConfiguredRootAndReadsSameBytes() {
        var properties = new AppProperties(null, null,
                new AppProperties.File("FILESYSTEM", temp, Duration.ofMinutes(15), Duration.ofMinutes(10)));
        var storage = new MockFileStorage(properties);
        assertThat(storage.provider()).isEqualTo("FILESYSTEM");
        assertThat(storage.supportsProvider("MOCK")).isTrue();
        byte[] bytes = new byte[] { 0, 1, 2, 3, -1 };
        storage.write("archive/7/document", bytes);
        assertThat(storage.exists("archive/7/document")).isTrue();
        assertThat(storage.read("archive/7/document")).isEqualTo(bytes);
        assertThat(temp.resolve("archive/7/document")).hasBinaryContent(bytes);
    }

    @Test
    void filesystemProviderRejectsMissingRootToAvoidSilentLocalFallback() {
        var properties = new AppProperties(null, null,
                new AppProperties.File("FILESYSTEM", temp.resolve("unmounted-nas"),
                        Duration.ofMinutes(15), Duration.ofMinutes(10)));
        assertThatThrownBy(() -> new MockFileStorage(properties))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("绝对路径");
    }

    @Test
    void copiedObjectsRemainReadableAfterChangingStorageRoot() throws Exception {
        Path originalRoot = Files.createDirectory(temp.resolve("local"));
        Path nasRoot = Files.createDirectory(temp.resolve("nas-mount"));
        String key = "archive/7/existing-file";
        byte[] bytes = "existing archive material".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var original = new MockFileStorage(new AppProperties(null, null,
                new AppProperties.File("MOCK", originalRoot, Duration.ofMinutes(15), Duration.ofMinutes(10))));
        original.write(key, bytes);
        Path destination = nasRoot.resolve(key);
        Files.createDirectories(destination.getParent());
        Files.copy(originalRoot.resolve(key), destination);

        var switched = new MockFileStorage(new AppProperties(null, null,
                new AppProperties.File("FILESYSTEM", nasRoot, Duration.ofMinutes(15), Duration.ofMinutes(10))));
        assertThat(switched.supportsProvider("MOCK")).isTrue();
        assertThat(switched.read(key)).isEqualTo(bytes);
    }
}
