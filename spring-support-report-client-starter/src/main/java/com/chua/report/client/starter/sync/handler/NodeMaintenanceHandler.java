package com.chua.report.client.starter.sync.handler;

import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.MapUtils;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
/**
 * @作者 CH
*/

@Slf4j
@Spi("nodeMaintenanceHandler")
public class NodeMaintenanceHandler implements SyncMessageHandler, ApplicationContextAware {

    /**
     * Backup_dir
    */
    private static final String BACKUP_DIR = "backups";
    /**
     * Upgrade_dir
    */
    private static final String UPGRADE_DIR = "upgrades";
    /**
     * Rollback_dir
    */
    private static final String ROLLBACK_DIR = "rollbacks";
    /**
     * Maintenance_Home_dir
    */
    private static final String MAINTENANCE_HOME_DIR = "plugin.report.maintenance.home-dir";
    /**
     * Maintenance_当前_artifact
    */
    private static final String MAINTENANCE_CURRENT_ARTIFACT = "plugin.report.maintenance.current-artifact";
    /**
     * 支持_包
    */
    private static final List<String> SUPPORTED_PACKAGES = List.of("jar", "zip", "tar.gz", "tgz");

    private final Map<String, RollbackSnapshot> rollbackSnapshots = new ConcurrentHashMap<>(); // rollbacksnapshots

    /**
     * Application上下文
    */
    private ApplicationContext applicationContext;

    @Override
    /**
     * 设置application上下文
    */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    /**
     * 获取名称
    */
    public String getName() {
        return "nodeMaintenanceHandler";
    }

    @Override
    /**
     * 支持
    */
    public boolean supports(String topic) {
        return MonitorTopics.NODE_BACKUP.equals(topic)
                || MonitorTopics.NODE_UPGRADE.equals(topic)
                || MonitorTopics.NODE_RESTORE.equals(topic);
    }

    @Override
    /**
     * 处理
    */
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
     * 处理Backup
     *
     * @param data 数据
     * @return 把手backup的结果
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
     * 创建Backup
     *
     * @param data 数据
     * @return 创建backup的结果
     */
    private Map<String, Object> createBackup(Map<String, Object> data) {
        try {
            String description = MapUtils.getString(data, "description", "手动备份");

            Map<String, Object> backupData = new LinkedHashMap<>();
            backupData.put("backupTime", System.currentTimeMillis());
            backupData.put("description", description);
            backupData.put("applicationName", getApplicationName());
            backupData.put("springVersion", org.springframework.boot.SpringBootVersion.getVersion());
            backupData.put("environment", collectEnvironmentProperties());
            backupData.put("systemProperties", collectSystemProperties());

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = String.format("backup_%s_%s.json", getApplicationName(), timestamp);

            Path backupDir = getBackupDirectory();
            Files.createDirectories(backupDir);
            Path backupFile = backupDir.resolve(backupFileName);

            Files.writeString(backupFile, Json.toJson(backupData), StandardCharsets.UTF_8);
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
     * 列表backups
     *
     * @return 列表backups的结果
     */
    private Map<String, Object> listBackups() {
        try {
            Path backupDir = getBackupDirectory();
            if (!Files.exists(backupDir)) {
                return Map.of("code", 200, "message", "成功", "data", List.of());
            }

            List<Map<String, Object>> backups = new ArrayList<>();
            /**
             * 尝试。
             * @param Files.list(backupDir 文件.列表(backupdir
             */
            try (var stream = Files.list(backupDir)) {
                stream.filter(path -> path.toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::getFileName).reversed())
                        .forEach(path -> {
                            try {
                                Map<String, Object> backup = new LinkedHashMap<>();
                                backup.put("fileName", path.getFileName().toString());
                                backup.put("size", Files.size(path));
                                backup.put("lastModified", Files.getLastModifiedTime(path).toMillis());

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
     * 删除Backup
     *
     * @param data 数据
     * @return 删除backup的结果
     */
    private Map<String, Object> deleteBackup(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定备份文件名");
            }

            Path backupFile = getBackupDirectory().resolve(fileName);
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
     * 获取backup内容
     *
     * @param data 数据
     * @return 获取backup内容的结果
     */
    private Map<String, Object> getBackupContent(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定备份文件名");
            }

            Path backupFile = getBackupDirectory().resolve(fileName);
            if (!Files.exists(backupFile)) {
                return Map.of("code", 404, "message", "备份文件不存在");
            }

            String content = Files.readString(backupFile, StandardCharsets.UTF_8);
            return Map.of("code", 200, "message", "成功", "data", Json.getJsonObject(content));
        } catch (Exception e) {
            log.error("[NodeMaintenance] 获取备份内容失败", e);
            return Map.of("code", 500, "message", "获取备份内容失败: " + e.getMessage());
        }
    }

    /**
     * 处理Upgrade
     *
     * @param data 数据
     * @return 把手upgrade的结果
     */
    private Map<String, Object> handleUpgrade(Map<String, Object> data) {
        String action = MapUtils.getString(data, "action", "status");

        return switch (action) {
            case "upload" -> uploadUpgradePackage(data);
            case "list" -> listUpgradePackages();
            case "execute" -> executeUpgrade(data);
            case "status" -> getUpgradeStatus();
            case "rollback" -> rollbackUpgrade(data);
            default -> Map.of("code", 400, "message", "未知升级操作: " + action);
        };
    }

    /**
     * uploadupgrade包
     *
     * @param data 数据
     * @return uploadupgrade包的结果
     */
    private Map<String, Object> uploadUpgradePackage(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            String fileContent = MapUtils.getString(data, "fileContent");

            if (fileName == null || fileContent == null) {
                return Map.of("code", 400, "message", "缺少文件名或文件内容");
            }

            byte[] fileBytes = Base64.getDecoder().decode(fileContent);
            Path upgradeDir = getUpgradeDirectory();
            Files.createDirectories(upgradeDir);
            Path upgradeFile = upgradeDir.resolve(fileName);
            Files.write(upgradeFile, fileBytes);

            log.info("[NodeMaintenance] 升级包上传成功: {}, 大小: {} bytes", fileName, fileBytes.length);

            return Map.of(
                    "code", 200,
                    "message", "升级包上传成功",
                    "data", Map.of("fileName", fileName, "size", fileBytes.length)
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 上传升级包失败", e);
            return Map.of("code", 500, "message", "上传升级包失败: " + e.getMessage());
        }
    }

    /**
     * 列表upgrade包
     *
     * @return 列表upgrade包的结果
     */
    private Map<String, Object> listUpgradePackages() {
        try {
            Path upgradeDir = getUpgradeDirectory();
            if (!Files.exists(upgradeDir)) {
                return Map.of("code", 200, "message", "成功", "data", List.of());
            }

            List<Map<String, Object>> packages = new ArrayList<>();
            /**
             * 尝试。
             * @param Files.list(upgradeDir 文件.列表(upgradedir
             */
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
     * 执行Upgrade
     *
     * @param data 数据
     * @return 执行upgrade的结果
     */
    private Map<String, Object> executeUpgrade(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            boolean autoRestart = MapUtils.getBoolean(data, "autoRestart", true);
            boolean autoBackup = MapUtils.getBoolean(data, "autoBackup", true);

            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定升级包");
            }

            Path upgradeFile = getUpgradeDirectory().resolve(fileName);
            if (!Files.exists(upgradeFile)) {
                return Map.of("code", 404, "message", "升级包不存在");
            }

            PackageType packageType = PackageType.fromFileName(fileName);
            if (packageType == null) {
                return Map.of("code", 400, "message", "不支持的升级包类型: " + fileName);
            }

            if (autoBackup) {
                Map<String, Object> backupResult = createBackup(Map.of("description", "升级前自动备份"));
                if ((int) backupResult.get("code") != 200) {
                    log.warn("[NodeMaintenance] 升级前备份失败: {}", backupResult.get("message"));
                }
            }

            Path applicationHome = getApplicationHomeDirectory();
            Path currentArtifact = getCurrentArtifactPath();
            if (currentArtifact == null || !Files.exists(currentArtifact)) {
                return Map.of("code", 500, "message", "无法获取当前应用路径");
            }

            RollbackSnapshot snapshot = switch (packageType) {
                case JAR -> executeJarUpgrade(upgradeFile, currentArtifact, applicationHome, packageType);
                case ZIP, TAR_GZ, TGZ -> executeArchiveUpgrade(upgradeFile, applicationHome, currentArtifact, packageType);
            };
            rollbackSnapshots.put(snapshot.rollbackId(), snapshot);

            if (autoRestart) {
                log.info("[NodeMaintenance] 准备重启应用...");
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
                    "data", buildUpgradeResult(snapshot, fileName, currentArtifact, autoRestart)
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 执行升级失败", e);
            return Map.of("code", 500, "message", "执行升级失败: " + e.getMessage());
        }
    }

    /**
     * 回滚Upgrade
     *
     * @param data 数据
     * @return rollbackUpgrade的结果
     */
    private Map<String, Object> rollbackUpgrade(Map<String, Object> data) {
        try {
            String rollbackId = MapUtils.getString(data, "rollbackId");
            if (rollbackId == null || rollbackId.isEmpty()) {
                return Map.of("code", 400, "message", "未指定回滚标识");
            }

            RollbackSnapshot snapshot = rollbackSnapshots.get(rollbackId);
            if (snapshot == null) {
                return Map.of("code", 404, "message", "回滚记录不存在");
            }

            /**
             * restorerollbacksnapshot。
             * @param snapshot snapshot
             */
            restoreRollbackSnapshot(snapshot);
            rollbackSnapshots.remove(rollbackId);
            /**
             * 删除目录quietly。
             * @param snapshot.rollbackDirectory( snapshot.rollback目录(
             */
            deleteDirectoryQuietly(snapshot.rollbackDirectory());

            return Map.of(
                    "code", 200,
                    "message", "回滚成功",
                    "data", Map.of(
                            "rollbackId", rollbackId,
                            "packageType", snapshot.packageType().displayName(),
                            "restoredFiles", snapshot.entries().size()
                    )
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 执行回滚失败", e);
            return Map.of("code", 500, "message", "执行回滚失败: " + e.getMessage());
        }
    }

    /**
     * 获取upgrade状态
     *
     * @return 获取upgrade状态的结果
     */
    private Map<String, Object> getUpgradeStatus() {
        try {
            Path applicationHome = getApplicationHomeDirectory();
            Path currentArtifact = getCurrentArtifactPath();

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("applicationName", getApplicationName());
            status.put("applicationHome", applicationHome.toString());
            status.put("currentVersion", getApplicationVersion());
            status.put("jarPath", currentArtifact != null ? currentArtifact.toString() : "未知");
            status.put("jarSize", currentArtifact != null && Files.exists(currentArtifact) ? Files.size(currentArtifact) : 0L);
            status.put("lastModified", currentArtifact != null && Files.exists(currentArtifact) ? Files.getLastModifiedTime(currentArtifact).toMillis() : 0L);
            status.put("supportedPackages", SUPPORTED_PACKAGES);

            return Map.of("code", 200, "message", "成功", "data", status);
        } catch (Exception e) {
            log.error("[NodeMaintenance] 获取升级状态失败", e);
            return Map.of("code", 500, "message", "获取升级状态失败: " + e.getMessage());
        }
    }

    /**
     * 处理Restore
     *
     * @param data 数据
     * @return 把手restore的结果
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
     * previewrestore
     *
     * @param data 数据
     * @return previewRestore的结果
     */
    private Map<String, Object> previewRestore(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定备份文件");
            }

            Path backupFile = getBackupDirectory().resolve(fileName);
            if (!Files.exists(backupFile)) {
                return Map.of("code", 404, "message", "备份文件不存在");
            }

            String content = Files.readString(backupFile, StandardCharsets.UTF_8);
            Map<String, Object> backupData = Json.getJsonObject(content);

            Map<String, Object> currentEnv = collectEnvironmentProperties();
            Map<String, Object> backupEnv = MapUtils.getMap(backupData, "environment");

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
     * 执行Restore
     *
     * @param data 数据
     * @return 执行restore的结果
     */
    private Map<String, Object> executeRestore(Map<String, Object> data) {
        try {
            String fileName = MapUtils.getString(data, "fileName");
            if (fileName == null || fileName.isEmpty()) {
                return Map.of("code", 400, "message", "未指定备份文件");
            }

            Path backupFile = getBackupDirectory().resolve(fileName);
            if (!Files.exists(backupFile)) {
                return Map.of("code", 404, "message", "备份文件不存在");
            }

            String content = Files.readString(backupFile, StandardCharsets.UTF_8);
            Map<String, Object> backupData = Json.getJsonObject(content);

            Map<String, Object> backupEnv = MapUtils.getMap(backupData, "environment");
            int restoredCount = 0;

            if (backupEnv != null) {
                for (Map.Entry<String, Object> entry : backupEnv.entrySet()) {
                    if (entry.getValue() != null) {
                        System.setProperty(entry.getKey(), entry.getValue().toString());
                        restoredCount++;
                    }
                }
            }

            log.info("[NodeMaintenance] 配置还原完成，已还原 {} 项配置", restoredCount);

            return Map.of(
                    "code", 200,
                    "message", "配置还原成功，部分配置需要重启后生效",
                    "data", Map.of("restoredCount", restoredCount, "backupFile", fileName, "needRestart", true)
            );
        } catch (Exception e) {
            log.error("[NodeMaintenance] 执行还原失败", e);
            return Map.of("code", 500, "message", "执行还原失败: " + e.getMessage());
        }
    }

    /**
     * 执行jarupgrade
     *
     * @param upgradeFile upgrade文件
     * @param currentArtifact 当前artifact
     * @param applicationHome applicationHome
     * @param packageType 包类型
     * @return 执行jarupgrade的结果
     */
    private RollbackSnapshot executeJarUpgrade(Path upgradeFile, Path currentArtifact, Path applicationHome, PackageType packageType) throws IOException {
        String rollbackId = createRollbackId();
        Path rollbackDir = getRollbackDirectory().resolve(rollbackId);
        Files.createDirectories(rollbackDir);

        List<RollbackEntry> entries = new ArrayList<>();
        entries.add(createRollbackEntry(currentArtifact, rollbackDir.resolve("artifact.bak")));

        Files.copy(upgradeFile, currentArtifact, StandardCopyOption.REPLACE_EXISTING);
        /**
         * rollbacksnapshot。
         * @param rollbackId rollbackid
         * @param packageType 包类型
         * @param applicationHome applicationHome
         * @param rollbackDir rollbackdir
         * @param entries entries
         * @return RollbackSnapshot的结果
         */
        return new RollbackSnapshot(rollbackId, packageType, applicationHome, rollbackDir, entries);
    }

    /**
     * 执行Arch Linux Linux Linuxupgrade
     *
     * @param upgradeFile upgrade文件
     * @param applicationHome applicationHome
     * @param currentArtifact 当前artifact
     * @param packageType 包类型
     * @return 执行Arch Linuxupgrade的结果
     */
    private RollbackSnapshot executeArchiveUpgrade(Path upgradeFile, Path applicationHome, Path currentArtifact, PackageType packageType) throws IOException {
        String rollbackId = createRollbackId();
        Path rollbackDir = getRollbackDirectory().resolve(rollbackId);
        Files.createDirectories(rollbackDir);

        Map<Path, byte[]> payloads = readArchivePayloads(upgradeFile, applicationHome, packageType);
        List<RollbackEntry> entries = new ArrayList<>();
        int index = 0;

        for (Map.Entry<Path, byte[]> entry : payloads.entrySet()) {
            Path target = entry.getKey();
            Path backupPath = rollbackDir.resolve("files").resolve(index++ + ".bak");
            entries.add(createRollbackEntry(target, backupPath));

            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, entry.getValue());
            /**
             * applyexecutable钻头。
             * @param target Target
             */
            applyExecutableBit(target);
        }

        /**
         * rollbacksnapshot。
         * @param rollbackId rollbackid
         * @param packageType 包类型
         * @param applicationHome applicationHome
         * @param rollbackDir rollbackdir
         * @param entries entries
         * @return RollbackSnapshot的结果
         */
        return new RollbackSnapshot(rollbackId, packageType, applicationHome, rollbackDir, entries);
    }

    /**
     * Restore回滚Snapshot
     *
     * @param snapshot snapshot
     */
    private void restoreRollbackSnapshot(RollbackSnapshot snapshot) throws IOException {
        List<RollbackEntry> entries = new ArrayList<>(snapshot.entries());
        Collections.reverse(entries);

        for (RollbackEntry entry : entries) {
            Path target = Paths.get(entry.targetPath());
            if (entry.existedBefore()) {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(Paths.get(entry.backupPath()), target, StandardCopyOption.REPLACE_EXISTING);
                /**
                 * applyexecutable钻头。
                 * @param target Target
                 */
                applyExecutableBit(target);
            } else {
                Files.deleteIfExists(target);
                /**
                 * cleanup空目录。
                 * @param target.getParent( Target.获取父(
                 */
                cleanupEmptyDirectories(target.getParent(), snapshot.applicationHome());
            }
        }
    }

    /**
     * 创建回滚Entry
     *
     * @param target Target
     * @param backupPath backup路径
     * @return 创建rollbackentry的结果
     */
    private RollbackEntry createRollbackEntry(Path target, Path backupPath) throws IOException {
        boolean existedBefore = Files.exists(target);
        String backupPathValue = null;

        if (existedBefore) {
            Path parent = backupPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(target, backupPath, StandardCopyOption.REPLACE_EXISTING);
            backupPathValue = backupPath.toString();
        }

        /**
         * rollbackentry。
         * @param target.toString( Target.转为字符串(
         * @return RollbackEntry的结果
         */
        return new RollbackEntry(target.toString(), existedBefore, backupPathValue);
    }

    /**
     * 读取Arch Linux Linux Linuxpayloads
     *
     * @param archiveFile Arch Linux Linux Linux文件
     * @param applicationHome applicationHome
     * @param packageType 包类型
     * @return 读取Arch Linuxpayloads的结果
     */
    private Map<Path, byte[]> readArchivePayloads(Path archiveFile, Path applicationHome, PackageType packageType) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();

        if (packageType == PackageType.ZIP) {
            /**
             * 尝试。
             * @param Files.newInputStream(archiveFile 文件.新输入流(Arch Linux Linux文件
             */
            try (InputStream inputStream = Files.newInputStream(archiveFile);
                 ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
                ZipEntry zipEntry;
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    if (zipEntry.isDirectory()) {
                        continue;
                    }
                    entries.put(normalizeArchiveEntryName(zipEntry.getName()), zipInputStream.readAllBytes());
                }
            }
        } else {
            /**
             * 尝试。
             * @param Files.newInputStream(archiveFile 文件.新输入流(Arch Linux Linux文件
             */
            try (InputStream inputStream = Files.newInputStream(archiveFile);
                 GzipCompressorInputStream gzipInputStream = new GzipCompressorInputStream(inputStream);
                 TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {
                TarArchiveEntry tarEntry;
                while ((tarEntry = tarInputStream.getNextTarEntry()) != null) {
                    if (tarEntry.isDirectory()) {
                        continue;
                    }
                    entries.put(normalizeArchiveEntryName(tarEntry.getName()), tarInputStream.readAllBytes());
                }
            }
        }

        Map<Path, byte[]> payloads = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            payloads.put(resolveArchiveTarget(applicationHome, entry.getKey()), entry.getValue());
        }
        return payloads;
    }

    /**
     * 构建upgrade结果
     *
     * @param snapshot snapshot
     * @param fileName 文件名称
     * @param currentArtifact 当前artifact
     * @param autoRestart autorestart
     * @return 构建upgrade结果的结果
     */
    private Map<String, Object> buildUpgradeResult(RollbackSnapshot snapshot, String fileName, Path currentArtifact, boolean autoRestart) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rollbackId", snapshot.rollbackId());
        data.put("packageType", snapshot.packageType().displayName());
        data.put("applicationHome", snapshot.applicationHome().toString());
        data.put("jarPath", currentArtifact.toString());
        data.put("upgradedFrom", currentArtifact.getFileName().toString());
        data.put("upgradedTo", fileName);
        data.put("changedFiles", snapshot.entries().size());
        data.put("autoRestart", autoRestart);
        return data;
    }

    /**
     * 获取backup目录
     *
     * @return 获取backup目录的结果
     */
    private Path getBackupDirectory() {
        return getApplicationHomeDirectory().resolve(BACKUP_DIR);
    }

    /**
     * 获取upgrade目录
     *
     * @return 获取upgrade目录的结果
     */
    private Path getUpgradeDirectory() {
        return getApplicationHomeDirectory().resolve(UPGRADE_DIR);
    }

    /**
     * 获取回滚目录
     *
     * @return 获取rollback目录的结果
     */
    private Path getRollbackDirectory() {
        return getApplicationHomeDirectory().resolve(ROLLBACK_DIR);
    }

    /**
     * 获取applicationHome目录
     *
     * @return 获取applicationHome目录的结果
     */
    private Path getApplicationHomeDirectory() {
        String configuredHome = getEnvironmentProperty(MAINTENANCE_HOME_DIR);
        if (configuredHome != null && !configuredHome.isBlank()) {
            return Paths.get(configuredHome).toAbsolutePath().normalize();
        }

        File homeDir = new ApplicationHome(getClass()).getDir();
        return homeDir.toPath().toAbsolutePath().normalize();
    }

    /**
     * 获取当前artifact路径
     *
     * @return 获取当前artifact路径的结果
     */
    private Path getCurrentArtifactPath() {
        String configuredArtifact = getEnvironmentProperty(MAINTENANCE_CURRENT_ARTIFACT);
        if (configuredArtifact != null && !configuredArtifact.isBlank()) {
            return Paths.get(configuredArtifact).toAbsolutePath().normalize();
        }

        File source = new ApplicationHome(getClass()).getSource();
        return source == null ? null : source.toPath().toAbsolutePath().normalize();
    }

    /**
     * 获取环境财产
     *
     * @param key Key
     * @return 获取环境财产的结果
     */
    private String getEnvironmentProperty(String key) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getEnvironment().getProperty(key);
    }

    /**
     * 是否支持upgrade包
     *
     * @param path 路径
     * @return 是否支持upgrade包的结果
     */
    private boolean isSupportedUpgradePackage(Path path) {
        return PackageType.fromFileName(path.getFileName().toString()) != null;
    }

    /**
     * normalizeArch Linux Linux Linuxentry名称
     *
     * @param entryName entry名称
     * @return normalizeArch Linuxentry名称的结果
     */
    private String normalizeArchiveEntryName(String entryName) {
        return entryName.replace('\\', '/');
    }

    /**
     * 解析Arch Linux Linux LinuxTarget
     *
     * @param applicationHome applicationHome
     * @param entryName entry名称
     * @return resolveArch LinuxTarget的结果
     */
    private Path resolveArchiveTarget(Path applicationHome, String entryName) {
        Path target = applicationHome.resolve(entryName).normalize();
        if (!target.startsWith(applicationHome)) {
            /**
             * IllegalArgumentException。
             * @param entryName entry名称
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("非法压缩包路径: " + entryName);
        }
        return target;
    }

    /**
     * 应用executable钻头
     *
     * @param target Target
     */
    private void applyExecutableBit(Path target) {
        String fileName = target.getFileName() == null ? "" : target.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".sh")) {
            target.toFile().setExecutable(true, false);
        }
    }

    /**
     * cleanup空目录
     *
     * @param start 启动
     * @param stopAt 停止at
     */
    private void cleanupEmptyDirectories(Path start, Path stopAt) throws IOException {
        Path current = start;
        while (current != null && stopAt != null && current.startsWith(stopAt) && !current.equals(stopAt)) {
            if (!Files.exists(current) || !Files.isDirectory(current)) {
                current = current.getParent();
                continue;
            }
            /**
             * 尝试。
             * @param Files.list(current 文件.列表(当前
             */
            try (var stream = Files.list(current)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    /**
     * 删除目录quietly
     *
     * @param directory 目录
     */
    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        /**
         * 尝试。
         * @param Files.walk(directory 文件.walk(目录
         */
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("[NodeMaintenance] 删除回滚目录失败: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("[NodeMaintenance] 清理回滚目录失败: {}", directory, e);
        }
    }

    /**
     * 创建回滚标识
     *
     * @return 创建rollbackid的结果
     */
    private String createRollbackId() {
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * collect环境属性
     *
     * @return collect环境属性的结果
     */
    private Map<String, Object> collectEnvironmentProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();

        if (applicationContext != null && applicationContext.getEnvironment() instanceof ConfigurableEnvironment env) {
            for (PropertySource<?> propertySource : env.getPropertySources()) {
                if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                    for (String name : enumerablePropertySource.getPropertyNames()) {
                        if (isSensitiveProperty(name)) {
                            continue;
                        }
                        try {
                            Object value = enumerablePropertySource.getProperty(name);
                            if (value != null) {
                                properties.put(name, value);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        return properties;
    }

    /**
     * collect系统属性
     *
     * @return collect系统属性的结果
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
     * 是否sensitive财产
     *
     * @param name 名称
     * @return 是否sensitive财产的结果
     */
    private boolean isSensitiveProperty(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("credential")
                || lower.contains("key")
                || lower.contains("token");
    }

    /**
     * 获取application名称
     *
     * @return 获取application名称的结果
     */
    private String getApplicationName() {
        if (applicationContext != null) {
            return applicationContext.getEnvironment().getProperty("spring.application.name", "unknown");
        }
        return "unknown";
    }

    /**
     * 获取application版本
     *
     * @return 获取application版本的结果
     */
    private String getApplicationVersion() {
        if (applicationContext != null) {
            return applicationContext.getEnvironment().getProperty("info.app.version", "1.0.0");
        }
        return "1.0.0";
    }

    /**
     * 包类型枚举。
     *
     * @author CH
     * @since 4.0.0
     */
    private enum PackageType {
        /**
         * JAR。
         * @param "JAR" "JAR"
         */
        JAR("JAR"),
        /**
         * 压缩。
         * @param "ZIP" "压缩"
         */
        ZIP("ZIP"),
        /**
         * 焦油gz。
         * @param "TAR_GZ" "焦油_GZ"
         */
        TAR_GZ("TAR_GZ"),
        /**
         * TGZ。
         * @param "TGZ" "TGZ"
         */
        TGZ("TGZ");

        /**
         * Display名称
        */
        private final String displayName;

        /**
         * 包类型。
         * @param displayName display名称
         */
        PackageType(String displayName) {
            this.displayName = displayName;
        }

        /**
         * display名称
         *
         * @return display名称的结果
         */
        private String displayName() {
            return displayName;
        }

        /**
         * 从文件名称
         *
         * @param fileName 文件名称
         * @return 从文件名称的结果
         */
        private static PackageType fromFileName(String fileName) {
            if (fileName == null) {
                return null;
            }
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".tar.gz")) {
                return TAR_GZ;
            }
            if (lower.endsWith(".tgz")) {
                return TGZ;
            }
            if (lower.endsWith(".zip")) {
                return ZIP;
            }
            if (lower.endsWith(".jar")) {
                return JAR;
            }
            return null;
        }
    }

    /**
     * 回滚Snapshot
     * @param rollbackId rollbackid
     * @param packageType 包类型
     * @param applicationHome applicationHome
     * @param rollbackDirectory rollback目录
     * @param entries entries
     * @param targetPath Target路径
     * @param existedBefore existed之前
     * @param backupPath backup路径
     */
    private record RollbackSnapshot(
            String rollbackId,
            PackageType packageType,
            Path applicationHome,
            Path rollbackDirectory,
            List<RollbackEntry> entries
    ) {
    }

    /**
     * 回滚Entry
     *
     * @param targetPath Target路径
     * @param existedBefore existed之前
     * @param backupPath backup路径
     * @return RollbackEntry的结果
     */
    private record RollbackEntry(String targetPath, boolean existedBefore, String backupPath) {
    }
}

