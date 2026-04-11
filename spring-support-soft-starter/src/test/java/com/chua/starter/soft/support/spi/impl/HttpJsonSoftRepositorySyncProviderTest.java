package com.chua.starter.soft.support.spi.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpJsonSoftRepositorySyncProviderTest {

    private final HttpJsonSoftRepositorySyncProvider provider = new HttpJsonSoftRepositorySyncProvider();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSyncFromEmbeddedHttpRepositoryWithBasicAuth() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/repo.json", exchange -> respondWithRepository(exchange, authorization));
        server.start();

        SoftRepository repository = new SoftRepository();
        repository.setSoftRepositoryId(1);
        repository.setAuthType("BASIC");
        repository.setUsername("soft-user");
        repository.setPassword("soft-pass");

        SoftRepositorySource source = SoftRepositorySource.builder()
                .sourceType("HTTP_JSON")
                .sourceUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/repo.json")
                .enabled(Boolean.TRUE)
                .build();

        var payload = provider.sync(repository, source);

        assertEquals("Basic " + Base64.getEncoder().encodeToString("soft-user:soft-pass".getBytes(StandardCharsets.UTF_8)), authorization.get());
        assertEquals(1, payload.packages().size());
        assertEquals(1, payload.versions().size());
        assertEquals("minio", payload.packages().get(0).getPackageCode());
        assertEquals("REPOSITORY", payload.versions().get(0).getCapabilityFlagsJson().replace("[", "").replace("]", "").replace("\"", ""));
        assertTrue(payload.versions().get(0).getDownloadUrlsJson().contains("minio-2026.tar.gz"));
        assertNotNull(payload.versions().get(0).getMetadataJson());
    }

    private void respondWithRepository(HttpExchange exchange, AtomicReference<String> authorization) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] body = """
                {
                  "packages": [
                    {
                      "packageCode": "minio",
                      "packageName": "MinIO",
                      "profileCode": "minio",
                      "packageCategory": "OBJECT_STORAGE",
                      "versions": [
                        {
                          "versionCode": "2026.04.05",
                          "versionName": "2026.04.05",
                          "downloadUrls": ["https://download.example/minio-2026.tar.gz"],
                          "capabilityFlags": ["REPOSITORY"],
                          "metadata": {
                            "maintainer": "soft-team"
                          }
                        }
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
