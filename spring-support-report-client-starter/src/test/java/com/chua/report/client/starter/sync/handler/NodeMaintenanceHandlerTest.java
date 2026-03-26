package com.chua.report.client.starter.sync.handler;

import com.chua.report.client.starter.sync.MonitorTopics;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class NodeMaintenanceHandlerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty("feature.mode");
    }

    @Test
    void shouldCreateListDownloadAndDeleteBackupsWithSensitiveValuesFiltered() throws Exception {
        Path appHome = Files.createDirectories(tempDir.resolve("app-home"));
        Path currentArtifact = writeFile(appHome.resolve("app.jar"), "v1");

        try (MaintenanceHarness harness = createHarness(appHome, currentArtifact, Map.of(
                "feature.mode", "blue",
                "db.password", "secret",
                "api.token", "masked"
        ))) {
            NodeMaintenanceHandler handler = harness.handler();

            Map<String, Object> created = cast(handler.handle(MonitorTopics.NODE_BACKUP, "s1", Map.of(
                    "action", "create",
                    "description", "本地备份"
            )));
            Map<String, Object> createdData = cast(created.get("data"));
            String fileName = createdData.get("fileName").toString();

            Map<String, Object> listed = cast(handler.handle(MonitorTopics.NODE_BACKUP, "s1", Map.of("action", "list")));
            List<Map<String, Object>> backups = castList(listed.get("data"));

            Map<String, Object> downloaded = cast(handler.handle(MonitorTopics.NODE_BACKUP, "s1", Map.of(
                    "action", "download",
                    "fileName", fileName
            )));
            Map<String, Object> downloadedData = cast(downloaded.get("data"));
            Map<String, Object> environment = cast(downloadedData.get("environment"));

            Map<String, Object> deleted = cast(handler.handle(MonitorTopics.NODE_BACKUP, "s1", Map.of(
                    "action", "delete",
                    "fileName", fileName
            )));
            Map<String, Object> emptyList = cast(handler.handle(MonitorTopics.NODE_BACKUP, "s1", Map.of("action", "list")));

            assertThat(created).containsEntry("code", 200);
            assertThat(backups).hasSize(1);
            assertThat(backups.get(0)).containsEntry("description", "本地备份");
            assertThat(environment)
                    .containsEntry("feature.mode", "blue")
                    .doesNotContainKeys("db.password", "api.token");
            assertThat(deleted).containsEntry("code", 200);
            assertThat(castList(emptyList.get("data"))).isEmpty();
        }
    }

    @Test
    void shouldReportUpgradeStatusFromOverridePaths() throws Exception {
        Path appHome = Files.createDirectories(tempDir.resolve("status-home"));
        Path currentArtifact = writeFile(appHome.resolve("agent.jar"), "v1");

        try (MaintenanceHarness harness = createHarness(appHome, currentArtifact, Map.of("info.app.version", "2026.03"))) {
            Map<String, Object> status = cast(harness.handler().handle(MonitorTopics.NODE_UPGRADE, "s2", Map.of("action", "status")));
            Map<String, Object> data = cast(status.get("data"));

            assertThat(status).containsEntry("code", 200);
            assertThat(data)
                    .containsEntry("applicationHome", appHome.toString())
                    .containsEntry("jarPath", currentArtifact.toString())
                    .containsEntry("currentVersion", "2026.03");
            assertThat(castStringList(data.get("supportedPackages"))).containsExactly("jar", "zip", "tar.gz", "tgz");
        }
    }

    @Test
    void shouldFailWhenUpgradePackageDoesNotExist() throws Exception {
        Path appHome = Files.createDirectories(tempDir.resolve("missing-home"));
        Path currentArtifact = writeFile(appHome.resolve("agent.jar"), "v1");

        try (MaintenanceHarness harness = createHarness(appHome, currentArtifact, Map.of())) {
            Map<String, Object> result = cast(harness.handler().handle(MonitorTopics.NODE_UPGRADE, "s3", Map.of(
                    "action", "execute",
                    "fileName", "missing.jar",
                    "autoRestart", false,
                    "autoBackup", false
            )));

            assertThat(result).containsEntry("code", 404);
            assertThat(result.get("message")).asString().contains("升级包不存在");
        }
    }

    @Test
    void shouldListJarZipAndTarGzPackages() throws Exception {
        Path appHome = Files.createDirectories(tempDir.resolve("package-home"));
        Path currentArtifact = writeFile(appHome.resolve("agent.jar"), "v1");

        try (MaintenanceHarness harness = createHarness(appHome, currentArtifact, Map.of())) {
            NodeMaintenanceHandler handler = harness.handler();
            uploadPackage(handler, createBinaryFile(tempDir.resolve("pkg/app-v2.jar"), "jar-v2"));
            uploadPackage(handler, createZipPackage(tempDir.resolve("pkg/app-v2.zip"), Map.of("config/app.yml", "zip-v2")));
            uploadPackage(handler, createTarGzPackage(tempDir.resolve("pkg/app-v2.tar.gz"), Map.of("bin/start.sh", "#!/bin/bash\necho tar")));

            Map<String, Object> listed = cast(handler.handle(MonitorTopics.NODE_UPGRADE, "s4", Map.of("action", "list")));
            List<Map<String, Object>> packages = castList(listed.get("data"));

            assertThat(packages).extracting(item -> item.get("fileName"))
                    .contains("app-v2.jar", "app-v2.zip", "app-v2.tar.gz");
        }
    }

    @Test
    void shouldExecuteJarUpgradeAndRollback() throws Exception {
        Path appHome = Files.createDirectories(tempDir.resolve("jar-home"));
        Path currentArtifact = writeFile(appHome.resolve("agent.jar"), "v1");

        try (MaintenanceHarness harness = createHarness(appHome, currentArtifact, Map.of())) {
            NodeMaintenanceHandler handler = harness.handler();
            Path upgradeJar = createBinaryFile(tempDir.resolve("pkg/agent-v2.jar"), "v2");
            uploadPackage(handler, upgradeJar);

            Map<String, Object> executed = cast(handler.handle(MonitorTopics.NODE_UPGRADE, "s5", Map.of(
                    "action", "execute",
                    "fileName", "agent-v2.jar",
                    "autoRestart", false,
                    "autoBackup", false
            )));
            Map<String, Object> executedData = cast(executed.get("data"));
            String rollbackId = executedData.get("rollbackId").toString();

            assertThat(Files.readString(currentArtifact, StandardCharsets.UTF_8)).isEqualTo("v2");
            assertThat(executedData).containsEntry("packageType", "JAR");

            Map<String, Object> rollback = cast(handler.handle(MonitorTopics.NODE_UPGRADE, "s5", Map.of(
                    "action", "rollback",
                    "rollbackId", rollbackId
            )));

            assertThat(rollback).containsEntry("code", 200);
            assertThat(Files.readString(currentArtifact, StandardCharsets.UTF_8)).isEqualTo("v1");
        }
    }

    @Test
    void shouldExecuteZipUpgradeAndRollbackSeparatedResources() throws Exception {
        Path appHome = Files.createDirectories(tempDir.resolve("zip-home"));
        Path currentArtifact = writeFile(appHome.resolve("agent.jar"), "v1");
        writeFile(appHome.resolve("config/application.yml"), "name: old");

        try (MaintenanceHarness harness = createHarness(appHome, currentArtifact, Map.of())) {
            NodeMaintenanceHandler handler = harness.handler();
            Path upgradeZip = createZipPackage(tempDir.resolve("pkg/agent-dist.zip"), Map.of(
                    "config/application.yml", "name: new",
                    "bin/start.sh", "#!/bin/bash\necho start"
            ));
            uploadPackage(handler, upgradeZip);

            Map<String, Object> executed = cast(handler.handle(MonitorTopics.NODE_UPGRADE, "s6", Map.of(
                    "action", "execute",
                    "fileName", "agent-dist.zip",
                    "autoRestart", false,
                    "autoBackup", false
            )));
            String rollbackId = cast(executed.get("data")).get("rollbackId").toString();

            assertThat(Files.readString(appHome.resolve("config/application.yml"), StandardCharsets.UTF_8)).isEqualTo("name: new");
            assertThat(appHome.resolve("bin/start.sh")).exists();

            Map<String, Object> rollback = cast(handler.handle(MonitorTopics.NODE_UPGRADE, "s6", Map.of(
                    "action", "rollback",
                    "rollbackId", rollbackId
            )));

            assertThat(rollback).containsEntry("code", 200);
            assertThat(Files.readString(appHome.resolve("config/application.yml"), StandardCharsets.UTF_8)).isEqualTo("name: old");
            assertThat(appHome.resolve("bin/start.sh")).doesNotExist();
        }
    }

    @Test
    void shouldExecuteTarGzUpgradeAndRollbackSeparatedResources() throws Exception {
        Path appHome = Files.createDirectories(tempDir.resolve("targz-home"));
        Path currentArtifact = writeFile(appHome.resolve("agent.jar"), "v1");
        writeFile(appHome.resolve("conf/bootstrap.yml"), "mode: old");

        try (MaintenanceHarness harness = createHarness(appHome, currentArtifact, Map.of())) {
            NodeMaintenanceHandler handler = harness.handler();
            Path upgradeTar = createTarGzPackage(tempDir.resolve("pkg/agent-dist.tar.gz"), Map.of(
                    "conf/bootstrap.yml", "mode: new",
                    "resources/banner.txt", "hello"
            ));
            uploadPackage(handler, upgradeTar);

            Map<String, Object> executed = cast(handler.handle(MonitorTopics.NODE_UPGRADE, "s7", Map.of(
                    "action", "execute",
                    "fileName", "agent-dist.tar.gz",
                    "autoRestart", false,
                    "autoBackup", false
            )));
            String rollbackId = cast(executed.get("data")).get("rollbackId").toString();

            assertThat(Files.readString(appHome.resolve("conf/bootstrap.yml"), StandardCharsets.UTF_8)).isEqualTo("mode: new");
            assertThat(Files.readString(appHome.resolve("resources/banner.txt"), StandardCharsets.UTF_8)).isEqualTo("hello");

            Map<String, Object> rollback = cast(handler.handle(MonitorTopics.NODE_UPGRADE, "s7", Map.of(
                    "action", "rollback",
                    "rollbackId", rollbackId
            )));

            assertThat(rollback).containsEntry("code", 200);
            assertThat(Files.readString(appHome.resolve("conf/bootstrap.yml"), StandardCharsets.UTF_8)).isEqualTo("mode: old");
            assertThat(appHome.resolve("resources/banner.txt")).doesNotExist();
        }
    }

    @Test
    void shouldPreviewAndRestoreConfigurationBackup() throws Exception {
        Path appHome = Files.createDirectories(tempDir.resolve("restore-home"));
        Path currentArtifact = writeFile(appHome.resolve("agent.jar"), "v1");

        String fileName;
        try (MaintenanceHarness legacyHarness = createHarness(appHome, currentArtifact, Map.of("feature.mode", "legacy"))) {
            Map<String, Object> created = cast(legacyHarness.handler().handle(MonitorTopics.NODE_BACKUP, "s8", Map.of(
                    "action", "create",
                    "description", "旧配置"
            )));
            fileName = cast(created.get("data")).get("fileName").toString();
        }

        try (MaintenanceHarness currentHarness = createHarness(appHome, currentArtifact, Map.of("feature.mode", "current"))) {
            NodeMaintenanceHandler handler = currentHarness.handler();

            Map<String, Object> preview = cast(handler.handle(MonitorTopics.NODE_RESTORE, "s8", Map.of(
                    "action", "preview",
                    "fileName", fileName
            )));
            Map<String, Object> previewData = cast(preview.get("data"));
            List<Map<String, Object>> differences = castList(previewData.get("differences"));

            Map<String, Object> restored = cast(handler.handle(MonitorTopics.NODE_RESTORE, "s8", Map.of(
                    "action", "execute",
                    "fileName", fileName
            )));

            assertThat(preview).containsEntry("code", 200);
            assertThat(previewData.get("differenceCount")).isEqualTo(1);
            assertThat(differences.get(0)).containsEntry("key", "feature.mode");
            assertThat(restored).containsEntry("code", 200);
            assertThat(System.getProperty("feature.mode")).isEqualTo("legacy");
        }
    }

    private void uploadPackage(NodeMaintenanceHandler handler, Path packagePath) throws IOException {
        Map<String, Object> upload = cast(handler.handle(MonitorTopics.NODE_UPGRADE, "upload", Map.of(
                "action", "upload",
                "fileName", packagePath.getFileName().toString(),
                "fileContent", Base64.getEncoder().encodeToString(Files.readAllBytes(packagePath))
        )));
        assertThat(upload).containsEntry("code", 200);
    }

    private Path createBinaryFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        return writeFile(path, content);
    }

    private Path createZipPackage(Path path, Map<String, String> entries) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream outputStream = Files.newOutputStream(path);
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return path;
    }

    private Path createTarGzPackage(Path path, Map<String, String> entries) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream outputStream = Files.newOutputStream(path);
             GzipCompressorOutputStream gzipOutputStream = new GzipCompressorOutputStream(outputStream);
             TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(gzipOutputStream)) {
            tarOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry archiveEntry = new TarArchiveEntry(entry.getKey());
                archiveEntry.setSize(bytes.length);
                tarOutputStream.putArchiveEntry(archiveEntry);
                tarOutputStream.write(bytes);
                tarOutputStream.closeArchiveEntry();
            }
            tarOutputStream.finish();
        }
        return path;
    }

    private Path writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private MaintenanceHarness createHarness(Path appHome,
                                             Path currentArtifact,
                                             Map<String, String> extraProperties) {
        NodeMaintenanceHandler handler = new NodeMaintenanceHandler();
        GenericApplicationContext context = new GenericApplicationContext();

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "report-agent")
                .withProperty("info.app.version", "1.0.0")
                .withProperty("plugin.report.maintenance.home-dir", appHome.toString())
                .withProperty("plugin.report.maintenance.current-artifact", currentArtifact.toString());
        extraProperties.forEach(environment::withProperty);

        context.setEnvironment(environment);
        context.refresh();
        handler.setApplicationContext(context);
        return new MaintenanceHarness(handler, context);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        return (List<String>) value;
    }

    private record MaintenanceHarness(NodeMaintenanceHandler handler,
                                      GenericApplicationContext context) implements AutoCloseable {
        @Override
        public void close() {
            context.close();
        }
    }
}
