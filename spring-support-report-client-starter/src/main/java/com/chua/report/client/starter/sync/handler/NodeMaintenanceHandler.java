package com.chua.report.client.starter.sync.handler;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.core.utils.MapUtils;
import com.chua.common.support.text.json.Json;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.starter.sync.support.spi.SyncMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.BeansException;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 节点维护处理器
 * <p>
 * 处理节点的备份、升级、还原操作
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/01/17
 */
@Slf4j
@Spi("nodeMaintenanceHandler")
public class NodeMaintenanceHandler implements SyncMessageHandler, ApplicationContextAware {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NodeMaintenanceHandler.class);

    private ApplicationContext applicationContext;

    /**
     * 备份目录
     */
    private static final String BACKUP_DIR = "backups";

    /**
     * 升级包目录
     */
    private static final String UPGRADE_DIR = "upgrades";

    /**
     * 回滚快照前缀
     */
    private static final String ROLLBACK_PREFIX = "rollback_";

    /**
     * 回滚元数据文件
     */
    private static final String ROLLBACK_METADATA_FILE = "rollback-metadata.json";

    /**
     * 测试/运维可覆写的应用目录
     */
    private static final String HOME_DIR_PROPERTY = "plugin.report.maintenance.home-dir";

    /**
     * 测试/运维可覆写的当前可执行物路径
     */
    private static final String CURRENT_ARTIFACT_PROPERTY = "plugin.report.maintenance.current-artifact";

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public String getName() {
        return "nodeMaintenanceHandler";
    }

    @Override
    public boolean supports(String topic) {
        return MonitorTopics.NODE_BACKUP.equals(topic)
                || MonitorTopics.NODE_UPGRADE.equals(topic)
                || MonitorTopics.NODE_RESTORE.equals(topic);
    }

    @Override
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        String action = MapUtils.getString(data, "action", topic);
        log.info("[NodeMaintenance] 收到维护命令: topic={}, action={}", topic, action);

        return switch (topic) {
            case MonitorTopics.NODE_BACKUP -> handleBackup(data);
            case MonitorTopics.NODE_UPGRADE -> handleUpgrade(data);
            case MonitorTopics.NODE_RESTORE -> handleRestore(data);
            default -> Map.of("code", 400, "message", "未知操作: " + topic);
        };
    }

    /**
     * 处理备份请求
     *
     * @param data 请求数据
     * @return 备份结果
     */
    private Map<String, Object> handleBackup(Map<String, Object> data) {
        String action = MapUtils.getString(data, "action", "create");

        return switch (action) {
            case "create" -> createBackup(data);
            case "list" -> listBackups();
            case "delete" -> deleteBackup(data);
            case "download" -> getBackupContent(data);
            default -> Map.of("code", 400, "message", "未知备份操作: " + action);
        };
    }

    /**
     * 创建备份
     *
     * @param data 请求数据
     * @return 备份结果
     */
    private Map<String, Object> createBackup(Map<String, Object> data) {
        try {
            String description = MapUtils.getString(data, "description", "手动备份");

            // 收集配置信息
            Map<String, Object> backupData = new LinkedHashMap<>();
            backupData.put("backupTime", System.currentTimeMillis());
            backupData.put("description", description);
            backupData.put("applicationName", getApplicationName());
            backupData.put("springVersion", org.springframework.boot.SpringBootVersion.getVersion());

            // 收集环境配置
            backupData.put("environment", collectEnvironmentProperties());

            // 收集系统属性
            backupData.put("systemProperties", collectSystemProperties());

            // 生成备份文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = String.format("backup_%s_%s.json", getApplicationName(), timestamp);

            // 保存备份文件
            Path backupDir = getBackupDirectory();
            Files.createDirectories(backupDir);
            Path backupFile = backupDir.resolve(backupFileName);

            String jsonContent = Json.toJson(backupData);
            Files.writeString(backupFile, jsonContent, StandardCharsets.UTF_8);

            log.info("[NodeMaintenance] 备份创建成功: {}", backupFile);

            return Map.of(
                    "code", 200,
                    "message", "备份创建成功",
                    "data", Map.of(
                            "fileName", backupFileName,
                            "filePath", backupFile.toString(),
                            "size", Files.size(backupFile),
                            "backupTime", backupData.get("backupTime")
                    )
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 创建备份失败", e);
            return Map.of("code", 500, "message", "创建备份失败: " + e.getMessage());
        }
    }

    /**
     * 获取备份列表
     *
     * @return 备份列表
     */
    private Map<String, Object> listBackups() {
        try {
            Path backupDir = getBackupDirectory();
            if (!Files.exists(backupDir)) {
                return Map.of("code", 200, "message", "成功", "data", List.of());
            }

            List<Map<String, Object>> backups = new ArrayList<>();
            try (var stream = Files.list(backupDir)) {
                stream.filter(path -> path.toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::getFileName).reversed())
                        .forEach(path -> {
                            try {
                                Map<String, Object> backup = new LinkedHashMap<>();
                                backup.put("fileName", path.getFileName().toString());
                                backup.put("size", Files.size(path));
                                backup.put("lastModified", Files.getLastModifiedTime(path).toMillis());

                                // 读取备份内容获取描述
                                String content = Files.readString(path, StandardCharsets.UTF_8);
                                Map<String, Object> backupData = Json.getJsonObject(content);
                                backup.put("description", MapUtils.getString(backupData, "description"));
                                backup.put("backupTime", MapUtils.getLong(backupData, "backupTime"));
                                backup.put("applicationName", MapUtils.getString(backupData, "applicationName"));

                                backups.add(backup);
                            } catch (IOException e) {
                                log.warn("[NodeMaintenance] 读取备份文件失败: {}", path, e);
                            }
                        });
            }

            return Map.of("code", 200, "message", "成功", "data", backups);
        } catch (Exception e) {
            log.error("[NodeMaintenance] 获取备份列表失败", e);
            return Map.of("code", 500, "message", "获取备份列表失败: " + e.getMessage());
        }
    }

    /**
     * 删除备份
     *
     * @param data 请求数据
     * @return 删除结果
     */
    private Map<String, Object> deleteBackup(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定备份文件名");
            }

            Path backupFile = resolveManagedFile(getBackupDirectory(), fileName, "备份文件");
            if (!Files.exists(backupFile)) {
                return Map.of("code", 404, "message", "备份文件不存在");
            }

            Files.delete(backupFile);
            log.info("[NodeMaintenance] 备份删除成功: {}", fileName);

            return Map.of("code", 200, "message", "备份删除成功");
        } catch (Exception e) {
            log.error("[NodeMaintenance] 删除备份失败", e);
            return Map.of("code", 500, "message", "删除备份失败: " + e.getMessage());
        }
    }

    /**
     * 获取备份内容
     *
     * @param data 请求数据
     * @return 备份内容
     */
    private Map<String, Object> getBackupContent(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定备份文件名");
            }

            Path backupFile = resolveManagedFile(getBackupDirectory(), fileName, "备份文件");
            if (!Files.exists(backupFile)) {
                return Map.of("code", 404, "message", "备份文件不存在");
            }

            String content = Files.readString(backupFile, StandardCharsets.UTF_8);
            Map<String, Object> backupData = Json.getJsonObject(content);

            return Map.of("code", 200, "message", "成功", "data", backupData);
        } catch (Exception e) {
            log.error("[NodeMaintenance] 获取备份内容失败", e);
            return Map.of("code", 500, "message", "获取备份内容失败: " + e.getMessage());
        }
    }

    /**
     * 处理升级请求
     *
     * @param data 请求数据
     * @return 升级结果
     */
    private Map<String, Object> handleUpgrade(Map<String, Object> data) {
        String action = MapUtils.getString(data, "action", "status");

        return switch (action) {
            case "upload" -> uploadUpgradePackage(data);
            case "list" -> listUpgradePackages();
            case "execute" -> executeUpgrade(data);
            case "rollback" -> rollbackUpgrade(data);
            case "status" -> getUpgradeStatus();
            default -> Map.of("code", 400, "message", "未知升级操作: " + action);
        };
    }

    /**
     * 上传升级包
     *
     * @param data 请求数据（包含 base64 编码的文件内容）
     * @return 上传结果
     */
    private Map<String, Object> uploadUpgradePackage(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            String fileContent = MapUtils.getString(data, "fileContent");

            if (fileName == null || fileContent == null) {
                return Map.of("code", 400, "message", "缺少文件名或文件内容");
            }

            // 解码 base64
            byte[] fileBytes = Base64.getDecoder().decode(fileContent);

            // 保存到升级目录
            Path upgradeDir = getUpgradeDirectory();
            Files.createDirectories(upgradeDir);
            Path upgradeFile = resolveManagedFile(upgradeDir, fileName, "升级包");
            Files.write(upgradeFile, fileBytes);

            log.info("[NodeMaintenance] 升级包上传成功: {}, 大小: {} bytes", fileName, fileBytes.length);

            return Map.of(
                    "code", 200,
                    "message", "升级包上传成功",
                    "data", Map.of(
                            "fileName", fileName,
                            "size", fileBytes.length
                    )
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 上传升级包失败", e);
            return Map.of("code", 500, "message", "上传升级包失败: " + e.getMessage());
        }
    }

    /**
     * 获取升级包列表
     *
     * @return 升级包列表
     */
    private Map<String, Object> listUpgradePackages() {
        try {
            Path upgradeDir = getUpgradeDirectory();
            if (!Files.exists(upgradeDir)) {
                return Map.of("code", 200, "message", "成功", "data", List.of());
            }

            List<Map<String, Object>> packages = new ArrayList<>();
            try (var stream = Files.list(upgradeDir)) {
                stream.filter(this::isSupportedUpgradePackage)
                        .sorted(Comparator.comparing(Path::getFileName).reversed())
                        .forEach(path -> {
                            try {
                                packages.add(Map.of(
                                        "fileName", path.getFileName().toString(),
                                        "size", Files.size(path),
                                        "lastModified", Files.getLastModifiedTime(path).toMillis()
                                ));
                            } catch (IOException e) {
                                log.warn("[NodeMaintenance] 读取升级包信息失败: {}", path, e);
                            }
                        });
            }

            return Map.of("code", 200, "message", "成功", "data", packages);
        } catch (Exception e) {
            log.error("[NodeMaintenance] 获取升级包列表失败", e);
            return Map.of("code", 500, "message", "获取升级包列表失败: " + e.getMessage());
        }
    }

    /**
     * 执行升级
     *
     * @param data 请求数据
     * @return 升级结果
     */
    private Map<String, Object> executeUpgrade(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            boolean autoRestart = MapUtils.getBoolean(data, "autoRestart", true);
            boolean autoBackup = MapUtils.getBoolean(data, "autoBackup", true);

            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定升级包");
            }

            Path upgradeFile = resolveManagedFile(getUpgradeDirectory(), fileName, "升级包");
            if (!Files.exists(upgradeFile)) {
                return Map.of("code", 404, "message", "升级包不存在");
            }

            UpgradePackageType packageType = detectPackageType(fileName);
            if (packageType == null) {
                return Map.of("code", 400, "message", "不支持的升级包类型: " + fileName);
            }

            // 自动备份当前配置
            if (autoBackup) {
                Map<String, Object> backupResult = createBackup(Map.of("description", "升级前自动备份"));
                if ((int) backupResult.get("code") != 200) {
                    log.warn("[NodeMaintenance] 升级前备份失败: {}", backupResult.get("message"));
                }
            }

            Path applicationHome = resolveHomeDirectory();
            Path currentArtifact = resolveCurrentArtifact();
            Path rollbackDir = createRollbackSnapshotDirectory();
            Map<String, Object> rollbackMetadata = applyUpgradePackage(
                    packageType,
                    upgradeFile,
                    applicationHome,
                    currentArtifact,
                    rollbackDir
            );
            writeRollbackMetadata(rollbackDir, rollbackMetadata);

            log.info("[NodeMaintenance] 升级包已应用: package={}, type={}, rollbackId={}",
                    upgradeFile, packageType.name(), rollbackMetadata.get("rollbackId"));

            // 如果需要自动重启
            if (autoRestart) {
                log.info("[NodeMaintenance] 准备重启应用...");
                // 延迟重启，让响应先返回
                Thread.startVirtualThread(() -> {
                    try {
                        Thread.sleep(2000);
                        System.exit(0);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            return Map.of(
                    "code", 200,
                    "message", autoRestart ? "升级成功，应用即将重启" : "升级成功，请手动重启应用",
                    "data", Map.of(
                            "upgradedFrom", currentArtifact != null ? currentArtifact.getFileName().toString() : "目录分发",
                            "upgradedTo", fileName,
                            "rollbackId", rollbackMetadata.get("rollbackId"),
                            "packageType", packageType.name(),
                            "autoRestart", autoRestart,
                            "applicationHome", applicationHome.toString()
                    )
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 执行升级失败", e);
            return Map.of("code", 500, "message", "执行升级失败: " + e.getMessage());
        }
    }

    /**
     * 回滚最近一次升级快照
     *
     * @param data 请求数据
     * @return 回滚结果
     */
    private Map<String, Object> rollbackUpgrade(Map<String, Object> data) {
        try {
            String rollbackId = MapUtils.getString(data, "rollbackId");
            if (rollbackId == null || rollbackId.isEmpty()) {
                return Map.of("code", 400, "message", "未指定回滚快照");
            }

            Path rollbackDir = resolveManagedFile(getBackupDirectory(), rollbackId, "回滚快照");
            Path metadataFile = rollbackDir.resolve(ROLLBACK_METADATA_FILE);
            if (!Files.exists(metadataFile)) {
                return Map.of("code", 404, "message", "回滚快照不存在");
            }

            String metadataContent = Files.readString(metadataFile, StandardCharsets.UTF_8);
            Map<String, Object> metadata = Json.getJsonObject(metadataContent);

            Path applicationHome = resolveHomeDirectory();
            List<String> addedFiles = toStringList(metadata.get("addedFiles"));
            addedFiles.sort(Comparator.comparingInt((String path) -> Path.of(path).getNameCount()).reversed());
            for (String addedFile : addedFiles) {
                Path target = resolveSafePath(applicationHome, addedFile);
                Files.deleteIfExists(target);
                deleteEmptyDirectories(target.getParent(), applicationHome);
            }

            Path originalDir = rollbackDir.resolve("original");
            for (String restoredFile : toStringList(metadata.get("restoredFiles"))) {
                Path backupFile = resolveSafePath(originalDir, restoredFile);
                Path target = resolveSafePath(applicationHome, restoredFile);
                Files.createDirectories(target.getParent());
                Files.copy(backupFile, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String artifactTarget = MapUtils.getString(metadata, "artifactTarget");
            String artifactBackup = MapUtils.getString(metadata, "artifactBackup");
            if (artifactTarget != null && !artifactTarget.isEmpty()
                    && artifactBackup != null && !artifactBackup.isEmpty()) {
                Files.copy(Path.of(artifactBackup), Path.of(artifactTarget), StandardCopyOption.REPLACE_EXISTING);
            }

            return Map.of(
                    "code", 200,
                    "message", "回滚成功",
                    "data", Map.of(
                            "rollbackId", rollbackId,
                            "restoredFiles", toStringList(metadata.get("restoredFiles")).size(),
                            "deletedFiles", addedFiles.size(),
                            "needRestart", true
                    )
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 回滚失败", e);
            return Map.of("code", 500, "message", "回滚失败: " + e.getMessage());
        }
    }

    /**
     * 获取升级状态
     *
     * @return 升级状态
     */
    private Map<String, Object> getUpgradeStatus() {
        try {
            Path currentArtifact = resolveCurrentArtifact();
            Path applicationHome = resolveHomeDirectory();

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("applicationName", getApplicationName());
            status.put("currentVersion", getApplicationVersion());
            status.put("applicationHome", applicationHome.toString());
            status.put("jarPath", currentArtifact != null ? currentArtifact.toAbsolutePath().toString() : "未知");
            status.put("jarSize", currentArtifact != null && Files.exists(currentArtifact) ? Files.size(currentArtifact) : 0);
            status.put("lastModified", currentArtifact != null && Files.exists(currentArtifact)
                    ? Files.getLastModifiedTime(currentArtifact).toMillis() : 0);
            status.put("supportedPackages", List.of("jar", "zip", "tar.gz", "tgz"));

            return Map.of("code", 200, "message", "成功", "data", status);
        } catch (Exception e) {
            log.error("[NodeMaintenance] 获取升级状态失败", e);
            return Map.of("code", 500, "message", "获取升级状态失败: " + e.getMessage());
        }
    }

    /**
     * 处理还原请求
     *
     * @param data 请求数据
     * @return 还原结果
     */
    private Map<String, Object> handleRestore(Map<String, Object> data) {
        String action = MapUtils.getString(data, "action", "preview");

        return switch (action) {
            case "preview" -> previewRestore(data);
            case "execute" -> executeRestore(data);
            default -> Map.of("code", 400, "message", "未知还原操作: " + action);
        };
    }

    /**
     * 预览还原（对比差异）
     *
     * @param data 请求数据
     * @return 差异信息
     */
    private Map<String, Object> previewRestore(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定备份文件");
            }

            Path backupFile = resolveManagedFile(getBackupDirectory(), fileName, "备份文件");
            if (!Files.exists(backupFile)) {
                return Map.of("code", 404, "message", "备份文件不存在");
            }

            // 读取备份内容
            String content = Files.readString(backupFile, StandardCharsets.UTF_8);
            Map<String, Object> backupData = Json.getJsonObject(content);

            // 获取当前配置
            Map<String, Object> currentEnv = collectEnvironmentProperties();
            Map<String, Object> backupEnv = MapUtils.getMap(backupData, "environment");

            // 对比差异
            List<Map<String, Object>> differences = new ArrayList<>();
            if (backupEnv != null) {
                for (Map.Entry<String, Object> entry : backupEnv.entrySet()) {
                    String key = entry.getKey();
                    Object backupValue = entry.getValue();
                    Object currentValue = currentEnv.get(key);

                    if (!Objects.equals(backupValue, currentValue)) {
                        differences.add(Map.of(
                                "key", key,
                                "backupValue", backupValue != null ? backupValue : "",
                                "currentValue", currentValue != null ? currentValue : "",
                                "status", currentValue == null ? "新增" : "修改"
                        ));
                    }
                }
            }

            return Map.of(
                    "code", 200,
                    "message", "成功",
                    "data", Map.of(
                            "backupTime", MapUtils.getLong(backupData, "backupTime"),
                            "description", MapUtils.getString(backupData, "description"),
                            "differenceCount", differences.size(),
                            "differences", differences
                    )
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 预览还原失败", e);
            return Map.of("code", 500, "message", "预览还原失败: " + e.getMessage());
        }
    }

    /**
     * 执行还原
     *
     * @param data 请求数据
     * @return 还原结果
     */
    private Map<String, Object> executeRestore(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定备份文件");
            }

            Path backupFile = resolveManagedFile(getBackupDirectory(), fileName, "备份文件");
            if (!Files.exists(backupFile)) {
                return Map.of("code", 404, "message", "备份文件不存在");
            }

            // 读取备份内容
            String content = Files.readString(backupFile, StandardCharsets.UTF_8);
            Map<String, Object> backupData = Json.getJsonObject(content);

            // 还原配置（通过系统属性设置，重启后生效）
            Map<String, Object> backupEnv = MapUtils.getMap(backupData, "environment");
            int restoredCount = 0;

            if (backupEnv != null) {
                for (Map.Entry<String, Object> entry : backupEnv.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value != null) {
                        System.setProperty(key, value.toString());
                        restoredCount++;
                    }
                }
            }

            log.info("[NodeMaintenance] 配置还原完成，已还原 {} 项配置", restoredCount);

            return Map.of(
                    "code", 200,
                    "message", "配置还原成功，部分配置需要重启后生效",
                    "data", Map.of(
                            "restoredCount", restoredCount,
                            "backupFile", fileName,
                            "needRestart", true
                    )
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 执行还原失败", e);
            return Map.of("code", 500, "message", "执行还原失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取备份目录
     */
    private Path getBackupDirectory() {
        return resolveHomeDirectory().resolve(BACKUP_DIR);
    }

    /**
     * 获取升级目录
     */
    private Path getUpgradeDirectory() {
        return resolveHomeDirectory().resolve(UPGRADE_DIR);
    }

    private Map<String, Object> applyUpgradePackage(UpgradePackageType packageType,
                                                    Path packageFile,
                                                    Path applicationHome,
                                                    Path currentArtifact,
                                                    Path rollbackDir) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String rollbackId = rollbackDir.getFileName().toString();
        metadata.put("rollbackId", rollbackId);
        metadata.put("packageFile", packageFile.getFileName().toString());
        metadata.put("packageType", packageType.name());
        metadata.put("appliedAt", System.currentTimeMillis());
        metadata.put("applicationHome", applicationHome.toString());

        List<String> restoredFiles = new ArrayList<>();
        List<String> addedFiles = new ArrayList<>();
        metadata.put("restoredFiles", restoredFiles);
        metadata.put("addedFiles", addedFiles);

        switch (packageType) {
            case JAR -> applyJarPackage(packageFile, currentArtifact, rollbackDir, metadata);
            case ZIP -> extractZipPackage(packageFile, applicationHome, rollbackDir.resolve("original"), restoredFiles, addedFiles);
            case TAR_GZ -> extractTarGzPackage(packageFile, applicationHome, rollbackDir.resolve("original"), restoredFiles, addedFiles);
        }
        return metadata;
    }

    private void applyJarPackage(Path packageFile,
                                 Path currentArtifact,
                                 Path rollbackDir,
                                 Map<String, Object> metadata) throws IOException {
        if (currentArtifact == null || !Files.exists(currentArtifact)) {
            throw new IOException("无法获取当前可执行物路径");
        }
        if (Files.isDirectory(currentArtifact)) {
            throw new IOException("当前可执行物是目录，JAR 升级请改用 zip/tar.gz 分发包");
        }

        Path artifactBackup = rollbackDir.resolve("artifact").resolve(currentArtifact.getFileName().toString());
        Files.createDirectories(artifactBackup.getParent());
        Files.copy(currentArtifact, artifactBackup, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(packageFile, currentArtifact, StandardCopyOption.REPLACE_EXISTING);

        metadata.put("artifactTarget", currentArtifact.toString());
        metadata.put("artifactBackup", artifactBackup.toString());
    }

    private void extractZipPackage(Path packageFile,
                                   Path applicationHome,
                                   Path originalDir,
                                   List<String> restoredFiles,
                                   List<String> addedFiles) throws IOException {
        try (ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(packageFile))) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                applyArchiveEntry(entry.getName(), entry.isDirectory(), inputStream, applicationHome, originalDir, restoredFiles, addedFiles);
            }
        }
    }

    private void extractTarGzPackage(Path packageFile,
                                     Path applicationHome,
                                     Path originalDir,
                                     List<String> restoredFiles,
                                     List<String> addedFiles) throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(packageFile);
             InputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
             InputStream gzipInputStream = new GzipCompressorInputStream(bufferedInputStream);
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextTarEntry()) != null) {
                applyArchiveEntry(entry.getName(), entry.isDirectory(), tarInputStream, applicationHome, originalDir, restoredFiles, addedFiles);
            }
        }
    }

    private void applyArchiveEntry(String entryName,
                                   boolean directory,
                                   InputStream inputStream,
                                   Path applicationHome,
                                   Path originalDir,
                                   List<String> restoredFiles,
                                   List<String> addedFiles) throws IOException {
        String normalizedEntryName = normalizeEntryName(entryName);
        if (normalizedEntryName.isEmpty()) {
            return;
        }

        Path target = resolveSafePath(applicationHome, normalizedEntryName);
        if (directory) {
            Files.createDirectories(target);
            return;
        }

        if (Files.exists(target)) {
            if (Files.isDirectory(target)) {
                throw new IOException("归档条目与目录冲突: " + normalizedEntryName);
            }
            if (!restoredFiles.contains(normalizedEntryName)) {
                Path backupFile = resolveSafePath(originalDir, normalizedEntryName);
                Files.createDirectories(backupFile.getParent());
                Files.copy(target, backupFile, StandardCopyOption.REPLACE_EXISTING);
                restoredFiles.add(normalizedEntryName);
            }
        } else if (!addedFiles.contains(normalizedEntryName)) {
            addedFiles.add(normalizedEntryName);
        }

        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeRollbackMetadata(Path rollbackDir, Map<String, Object> metadata) throws IOException {
        Files.createDirectories(rollbackDir);
        Files.writeString(rollbackDir.resolve(ROLLBACK_METADATA_FILE), Json.toJson(metadata), StandardCharsets.UTF_8);
    }

    private Path createRollbackSnapshotDirectory() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path rollbackDir = getBackupDirectory().resolve(ROLLBACK_PREFIX + timestamp);
        Files.createDirectories(rollbackDir);
        return rollbackDir;
    }

    private boolean isSupportedUpgradePackage(Path path) {
        return detectPackageType(path.getFileName().toString()) != null;
    }

    private UpgradePackageType detectPackageType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return UpgradePackageType.TAR_GZ;
        }
        if (lower.endsWith(".zip")) {
            return UpgradePackageType.ZIP;
        }
        if (lower.endsWith(".jar")) {
            return UpgradePackageType.JAR;
        }
        return null;
    }

    private Path resolveHomeDirectory() {
        String configuredPath = getEnvironmentProperty(HOME_DIR_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath).toAbsolutePath().normalize();
        }

        ApplicationHome home = new ApplicationHome(getClass());
        File homeDir = home.getDir();
        return homeDir.toPath().toAbsolutePath().normalize();
    }

    private Path resolveCurrentArtifact() {
        String configuredPath = getEnvironmentProperty(CURRENT_ARTIFACT_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath).toAbsolutePath().normalize();
        }

        ApplicationHome home = new ApplicationHome(getClass());
        File source = home.getSource();
        return source != null ? source.toPath().toAbsolutePath().normalize() : null;
    }

    private String getEnvironmentProperty(String propertyName) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getEnvironment().getProperty(propertyName);
    }

    private Path resolveManagedFile(Path baseDirectory, String fileName, String description) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IOException(description + "不能为空");
        }
        return resolveSafePath(baseDirectory, fileName);
    }

    private Path resolveSafePath(Path baseDirectory, String relativePath) throws IOException {
        Path normalizedBase = baseDirectory.toAbsolutePath().normalize();
        Path resolvedPath = normalizedBase.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(normalizedBase)) {
            throw new IOException("非法路径: " + relativePath);
        }
        return resolvedPath;
    }

    private String normalizeEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private void deleteEmptyDirectories(Path current, Path stopAt) throws IOException {
        Path normalizedStopAt = stopAt.toAbsolutePath().normalize();
        Path cursor = current;
        while (cursor != null && cursor.startsWith(normalizedStopAt) && !cursor.equals(normalizedStopAt)) {
            if (!Files.isDirectory(cursor)) {
                break;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(cursor)) {
                if (stream.iterator().hasNext()) {
                    break;
                }
            }
            Files.deleteIfExists(cursor);
            cursor = cursor.getParent();
        }
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }

        List<String> results = new ArrayList<>(listValue.size());
        for (Object item : listValue) {
            if (item != null) {
                results.add(item.toString());
            }
        }
        return results;
    }

    /**
     * 收集环境配置
     */
    private Map<String, Object> collectEnvironmentProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();

        if (applicationContext != null) {
            ConfigurableEnvironment env = (ConfigurableEnvironment) applicationContext.getEnvironment();

            for (PropertySource<?> propertySource : env.getPropertySources()) {
                if (propertySource instanceof EnumerablePropertySource<?> eps) {
                    for (String name : eps.getPropertyNames()) {
                        // 跳过敏感信息
                        if (isSensitiveProperty(name)) {
                            continue;
                        }
                        try {
                            Object value = eps.getProperty(name);
                            if (value != null) {
                                properties.put(name, value);
                            }
                        } catch (Exception e) {
                            // 忽略无法获取的属性
                        }
                    }
                }
            }
        }

        return properties;
    }

    /**
     * 收集系统属性
     */
    private Map<String, Object> collectSystemProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("java.version", System.getProperty("java.version"));
        properties.put("java.vendor", System.getProperty("java.vendor"));
        properties.put("os.name", System.getProperty("os.name"));
        properties.put("os.version", System.getProperty("os.version"));
        properties.put("user.timezone", System.getProperty("user.timezone"));
        return properties;
    }

    /**
     * 判断是否为敏感属性
     */
    private boolean isSensitiveProperty(String name) {
        String lower = name.toLowerCase();
        return lower.contains("password") 
                || lower.contains("secret") 
                || lower.contains("credential")
                || lower.contains("key")
                || lower.contains("token");
    }

    /**
     * 获取应用名称
     */
    private String getApplicationName() {
        if (applicationContext != null) {
            return applicationContext.getEnvironment()
                    .getProperty("spring.application.name", "unknown");
        }
        return "unknown";
    }

    /**
     * 获取应用版本
     */
    private String getApplicationVersion() {
        if (applicationContext != null) {
            return applicationContext.getEnvironment()
                    .getProperty("info.app.version", "1.0.0");
        }
        return "1.0.0";
    }

    private enum UpgradePackageType {
        JAR,
        ZIP,
        TAR_GZ
    }
}
