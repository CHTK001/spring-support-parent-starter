package com.chua.starter.filesystem.support.template;

import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.request.GetObjectRequest;
import com.chua.common.support.storage.request.ListObjectRequest;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.*;
import com.chua.common.support.storage.setting.BucketSetting;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageProperties.ProviderConfig;
import lombok.Getter;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.starter.common.support.logger.ModuleLog.*;

/**
 * 文件存储模板 (多 提供者 模式)。
 *
 * <p>从 {@link FileStorageProperties#providers} 读取多个 Provider 配置,
 * 每个 提供者 名称对应一个 {@link FileStorage} SPI 实例。
 * 通过 SPI 名称自动匹配: 显式 {@code type} > 端点 推断 > 兜底 minio。</p>
 *
 * @since 2024/12/28
 * @author CH
 */
public class FileStorageTemplate implements AutoCloseable {

    @Getter
    public final Map<String, FileStorage> storageMap = new ConcurrentHashMap<>(); // storage映射

    /** 默认存储名称 */
    private String defaultStorageName;

    /** 属性 */
    private final FileStorageProperties properties;

    /** 日志 */
    private final ModuleLog log = ModuleLog.of("FileStorage", FileStorageTemplate.class);

    /**
     * 创建 文件storagetemplate 实例
     * @param properties 属性
     */
    public FileStorageTemplate(FileStorageProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化所有 提供者。
     */
    public void initialize() {
        if (properties.getProviders() == null || properties.getProviders().isEmpty()) {
            log.warn("未配置任何 Provider");
            return;
        }
        for (Map.Entry<String, ProviderConfig> entry : properties.getProviders().entrySet()) {
            String name = entry.getKey();
            ProviderConfig config = entry.getValue();
            try {
                FileStorage storage = createFileStorage(config);
                if (storage != null) {
                    storageMap.put(name, storage);
                    log.info("注册 Provider: {} ({})", highlight(name), highlight(inferType(config)));
                    if (defaultStorageName == null
                            || (properties.getDefaultProvider() != null && properties.getDefaultProvider().equals(name))) {
                        defaultStorageName = name;
                    }
                }
            } catch (Exception e) {
                log.error("创建 Provider 失败: {} - {}", name, e.getMessage(), e);
            }
        }
        if (defaultStorageName != null) {
            log.info("默认 Provider: {}", highlight(defaultStorageName));
        }
    }

    /**
     * 创建文件storage
     *
     * @param config 配置
     * @return 创建文件storage的结果
     */
    private FileStorage createFileStorage(ProviderConfig config) {
        BucketSetting setting = BucketSetting.builder()
                .bucket(config.getBucket())
                .endpoint(config.getEndpoint())
                .accessKeyId(config.getAccessKey())
                .accessKeySecret(config.getSecretKey())
                .region(config.getRegion())
                .build();
        return FileStorage.createStorage(inferType(config), setting);
    }

    /**
     * 推断 提供者 的 SPI 类型。
     * @param config 配置
     * @return infer类型的结果
     */
    private String inferType(ProviderConfig config) {
        if (config.getType() != null && !config.getType().isEmpty()) {
            return config.getType();
        }
        String endpoint = config.getEndpoint();
        if (endpoint == null || endpoint.isEmpty()) {
            return "local";
        }
        String lower = endpoint.toLowerCase();
        if (lower.contains("oss.aliyuncs")) {
            return "oss";
        }
        if (lower.contains("myhuaweicloud") || lower.contains("obs.")) {
            return "obs";
        }
        if (lower.contains("cos.") || lower.contains("tencentyun")) {
            return "cos";
        }
        if (lower.contains("amazonaws")) {
            return "s3";
        }
        return "minio";
    }

    // ==================== 存储管理 ====================

    /**
     * 获取Storage
     *
     * @param name 名称
     * @return 获取storage的结果
     */
    public FileStorage getStorage(String name) {
        return storageMap.get(name);
    }

    /**
     * 获取默认storage
     *
     * @return 获取默认storage的结果
     */
    public FileStorage getDefaultStorage() {
        return defaultStorageName != null ? storageMap.get(defaultStorageName) : null;
    }

    /**
     * 获取默认storage名称
     *
     * @return 获取默认storage名称的结果
     */
    public String getDefaultStorageName() {
        return defaultStorageName;
    }

    /**
     * 添加Storage
     *
     * @param name 名称
     * @param storage storage
     */
    public void addStorage(String name, FileStorage storage) {
        storageMap.put(name, storage);
        log.info("动态添加存储: {}", highlight(name));
    }

    /**
     * 移除Storage
     *
     * @param name 名称
     */
    public void removeStorage(String name) {
        FileStorage storage = storageMap.remove(name);
        if (storage != null) {
            IoUtils.closeQuietly(storage);
            log.info("移除存储: {}", highlight(name));
        }
    }

    /**
     * 获取storage名称
     *
     * @return 获取storage名称的结果
     */
    public List<String> getStorageNames() {
        return List.copyOf(storageMap.keySet());
    }

    /**
     * 获取Storages
     *
     * @return 获取storages的结果
     */
    public List<FileStorage> getStorages() {
        return List.copyOf(storageMap.values());
    }

    // ==================== 文件操作（默认存储） ====================

    /**
     * 放入对象
     *
     * @param request 请求
     * @return 放入对象的结果
     */
    public PutObjectResult putObject(PutObjectRequest request) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return PutObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("默认 Provider 不存在")
                    .build();
        }
        return storage.putObject(request);
    }

    /**
     * 放入对象
     *
     * @param file 文件
     * @return 放入对象的结果
     */
    public PutObjectResult putObject(File file) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return PutObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("默认 Provider 不存在")
                    .build();
        }
        return storage.putObject(PutObjectRequest.of(file));
    }

    /**
     * 获取对象
     *
     * @param key 键
     * @return 获取对象的结果
     */
    public GetObjectResult getObject(String key) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("默认 Provider 不存在")
                    .build();
        }
        return storage.getObject(key);
    }

    /**
     * 获取对象
     *
     * @param request 请求
     * @return 获取对象的结果
     */
    public GetObjectResult getObject(GetObjectRequest request) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("默认 Provider 不存在")
                    .build();
        }
        return storage.getObject(request);
    }

    /**
     * 删除对象
     *
     * @param key 键
     * @return 删除对象的结果
     */
    public DeleteObjectResult deleteObject(String key) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return DeleteObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("默认 Provider 不存在")
                    .build();
        }
        return storage.deleteObject(key);
    }

    /**
     * 列表对象
     *
     * @param request 请求
     * @return 列表对象的结果
     */
    public ListObjectResult listObject(ListObjectRequest request) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return ListObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("默认 Provider 不存在")
                    .build();
        }
        return storage.listObject(request);
    }

    // ==================== 文件操作（指定 Provider） ====================

    /**
     * 放入对象
     *
     * @param providerName 提供者名称
     * @param request 请求
     * @return 放入对象的结果
     */
    public PutObjectResult putObject(String providerName, PutObjectRequest request) {
        FileStorage storage = getStorage(providerName);
        if (storage == null) {
            return PutObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("Provider 不存在: " + providerName)
                    .build();
        }
        return storage.putObject(request);
    }

    /**
     * 获取对象
     *
     * @param providerName 提供者名称
     * @param key 键
     * @return 获取对象的结果
     */
    public GetObjectResult getObject(String providerName, String key) {
        FileStorage storage = getStorage(providerName);
        if (storage == null) {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("Provider 不存在: " + providerName)
                    .build();
        }
        return storage.getObject(key);
    }

    /**
     * 删除对象
     *
     * @param providerName 提供者名称
     * @param key 键
     * @return 删除对象的结果
     */
    public DeleteObjectResult deleteObject(String providerName, String key) {
        FileStorage storage = getStorage(providerName);
        if (storage == null) {
            return DeleteObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("Provider 不存在: " + providerName)
                    .build();
        }
        return storage.deleteObject(key);
    }

    @Override
    /** 关闭 */
    public void close() {
        for (Map.Entry<String, FileStorage> entry : storageMap.entrySet()) {
            try {
                entry.getValue().close();
                log.info("关闭存储: {}", entry.getKey());
            } catch (Exception e) {
                log.error("关闭存储失败: {}", entry.getKey(), e);
            }
        }
        storageMap.clear();
    }
}
