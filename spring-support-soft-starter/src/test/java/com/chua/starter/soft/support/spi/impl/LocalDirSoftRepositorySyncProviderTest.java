package com.chua.starter.soft.support.spi.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDirSoftRepositorySyncProviderTest {

    private final LocalDirSoftRepositorySyncProvider provider = new LocalDirSoftRepositorySyncProvider();

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadIndexJsonFromLocalDirectoryRepository() throws Exception {
        Files.writeString(tempDir.resolve("index.json"), """
                {
                  "packages": [
                    {
                      "packageCode": "mysql-community",
                      "packageName": "MySQL Community",
                      "profileCode": "mysql",
                      "versions": [
                        {
                          "versionCode": "8.0.36",
                          "versionName": "8.0.36",
                          "downloadUrls": ["https://repo.example/mysql-8.0.36.tar.gz"],
                          "logPaths": ["/srv/mysql/logs/error.log"],
                          "configPaths": ["/srv/mysql/conf/my.cnf"],
                          "capabilityFlags": ["SERVICE", "CONFIG"]
                        }
                      ]
                    }
                  ]
                }
                """);
        SoftRepository repository = new SoftRepository();
        repository.setSoftRepositoryId(1);
        SoftRepositorySource source = SoftRepositorySource.builder()
                .sourceType("LOCAL_DIR")
                .localDirectory(tempDir.toString())
                .enabled(Boolean.TRUE)
                .build();

        var payload = provider.sync(repository, source);

        assertEquals(1, payload.packages().size());
        assertEquals(1, payload.versions().size());
        assertEquals("mysql-community", payload.packages().get(0).getPackageCode());
        assertEquals("8.0.36", payload.versions().get(0).getVersionCode());
        assertTrue(payload.versions().get(0).getDownloadUrlsJson().contains("mysql-8.0.36.tar.gz"));
    }

    @Test
    void shouldAggregateMultipleJsonFilesWhenIndexAbsent() throws Exception {
        Files.writeString(tempDir.resolve("redis.json"), """
                [
                  {
                    "packageCode": "redis",
                    "packageName": "Redis",
                    "versions": [
                      {
                        "versionCode": "7.2.4",
                        "downloadUrl": "https://repo.example/redis-7.2.4.tar.gz"
                      }
                    ]
                  }
                ]
                """);
        Files.writeString(tempDir.resolve("nginx.json"), """
                {
                  "packages": [
                    {
                      "packageCode": "nginx",
                      "packageName": "Nginx",
                      "versions": [
                        {
                          "versionCode": "1.26.0",
                          "downloadUrls": ["https://repo.example/nginx-1.26.0.tar.gz"]
                        }
                      ]
                    }
                  ]
                }
                """);
        SoftRepository repository = new SoftRepository();
        SoftRepositorySource source = SoftRepositorySource.builder()
                .sourceType("LOCAL_DIR")
                .localDirectory(tempDir.toString())
                .enabled(Boolean.TRUE)
                .build();

        var payload = provider.sync(repository, source);

        assertEquals(2, payload.packages().size());
        assertEquals(2, payload.versions().size());
        assertNotNull(payload.versions().stream()
                .filter(item -> "redis".equals(item.getPackageCode()))
                .findFirst()
                .orElse(null));
        assertNotNull(payload.versions().stream()
                .filter(item -> "nginx".equals(item.getPackageCode()))
                .findFirst()
                .orElse(null));
    }
}
