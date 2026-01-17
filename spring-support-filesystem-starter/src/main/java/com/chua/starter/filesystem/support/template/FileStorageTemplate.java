package com.chua.starter.filesystem.support.template;

import com.chua.common.support.storage.oss.FileStorage;
import com.chua.common.support.storage.oss.request.GetObjectRequest;
import com.chua.common.support.storage.oss.request.ListObjectRequest;
import com.chua.common.support.storage.oss.request.PutObjectRequest;
import com.chua.common.support.storage.oss.result.*;
import com.chua.common.support.storage.oss.result.ResultCode;
import com.chua.common.support.storage.oss.setting.BucketSetting;
import com.chua.common.support.core.utils.FileUtils;
import com.chua.common.support.core.utils.IoUtils;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import lombok.Getter;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.starter.common.support.logger.ModuleLog.*;

/**
 * 文件存储模板
 * <p>
 * 提供统一的文件存储操作接口，支持多种存储后端
 * </p>
 *
 * @author CH
 * @since 2024/12/28
 */
public class FileStorageTemplate implements AutoCloseable {

    /**
     * 存储实例映射
     */
    @Getter
    public final Map<String, FileStorage> storageMap = new ConcurrentHashMap<>();

    /**
     * 默认存储名称
     */
    private String defaultStorageName;

    /**
     * 配置属性
     */
    private final FileStorageProperties properties;

    /**
     * 日志
     */
    private final ModuleLog log = ModuleLog.of("FileStorage", FileStorageTemplate.class);

    public FileStorageTemplate(FileStorageProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化存储
     */
    public void initialize() {
        List<FileStorageProperties.StorageConfig> storages = properties.storages;
        if (storages == null || storages.isEmpty()) {
            log.warn("未配置任何存储后端");
            return;
        }

        for (FileStorageProperties.StorageConfig config : storages) {
            try {
                FileStorage storage = createFileStorage(config);
                if (storage != null) {
                    storageMap.put(config.name, storage);
                    log.info("注册存储: {} ({})", highlight(config.name), highlight(config.type));

                    if (config.defaultStorage || defaultStorageName == null) {
                        defaultStorageName = config.name;
                    }
                }
            } catch (Exception e) {
                log.error("创建存储失败: {} - {}", config.name, e.getMessage(), e);
            }
        }

        if (defaultStorageName != null) {
            log.info("默认存储: {}", highlight(defaultStorageName));
        }
    }

    /**
     * 创建文件存储实例
     */
    private FileStorage createFileStorage(FileStorageProperties.StorageConfig config) {
        BucketSetting bucketSetting = BucketSetting.builder()
                .bucket(config.bucket)
                .endpoint(config.endpoint)
                .accessKeyId(config.accessKeyId)
                .accessKeySecret(config.accessKeySecret)
                .region(config.region)
                .build();

        return FileStorage.createStorage(config.type, bucketSetting);
    }

    // ==================== 存储管理 ====================

    /**
     * 获取存储实例
     *
     * @param name 存储名称
     * @return 存储实例
     */
    public FileStorage getStorage(String name) {
        return storageMap.get(name);
    }

    /**
     * 获取默认存储实例
     *
     * @return 默认存储实例
     */
    public FileStorage getDefaultStorage() {
        return defaultStorageName != null ? storageMap.get(defaultStorageName) : null;
    }

    /**
     * 添加存储实例
     *
     * @param name    存储名称
     * @param storage 存储实例
     */
    public void addStorage(String name, FileStorage storage) {
        storageMap.put(name, storage);
        log.info("动态添加存储: {}", highlight(name));
    }

    /**
     * 移除存储实例
     *
     * @param name 存储名称
     */
    public void removeStorage(String name) {
        FileStorage storage = storageMap.remove(name);
        if (storage != null) {
            IoUtils.closeQuietly(storage);
            log.info("移除存储: {}", highlight(name));
        }
    }

    /**
     * 获取所有存储名称
     *
     * @return 存储名称列表
     */
    public List<String> getStorageNames() {
        return List.copyOf(storageMap.keySet());
    }

    // ==================== 文件操作（使用默认存储） ====================

    /**
     * 上传文件
     *
     * @param request 上传请求
     * @return 上传结果
     */
    public PutObjectResult putObject(PutObjectRequest request) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return PutObjectResult.builder()
                    .resultCode(ResultCode.FAILURE)
                    .message("默认存储不存在")
                    .build();
        }
        return storage.putObject(request);
    }

    /**
     * 上传文件
     *
     * @param file 本地文件
     * @return 上传结果
     */
    public PutObjectResult putObject(File file) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return PutObjectResult.builder()
                    .resultCode(ResultCode.FAILURE)
                    .message("默认存储不存在")
                    .build();
        }
        return storage.putObject(file);
    }

    /**
     * 获取文件
     *
     * @param key 文件键
     * @return 获取结果
     */
    public GetObjectResult getObject(String key) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return GetObjectResult.builder()
                    .resultCode(ResultCode.FAILURE)
                    .message("默认存储不存在")
                    .build();
        }
        return storage.getObject(key);
    }

    /**
     * 获取文件
     *
     * @param request 获取请求
     * @return 获取结果
     */
    public GetObjectResult getObject(GetObjectRequest request) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return GetObjectResult.builder()
                    .resultCode(ResultCode.FAILURE)
                    .message("默认存储不存在")
                    .build();
        }
        return storage.getObject(request);
    }

    /**
     * 删除文件
     *
     * @param key 文件键
     * @return 删除结果
     */
    public DeleteObjectResult deleteObject(String key) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return DeleteObjectResult.builder()
                    .resultCode(ResultCode.FAILURE)
                    .message("默认存储不存在")
                    .build();
        }
        return storage.deleteObject(key);
    }

    /**
     * 列出文件
     *
     * @param request 列表请求
     * @return 列表结果
     */
    public ListObjectResult listObject(ListObjectRequest request) {
        FileStorage storage = getDefaultStorage();
        if (storage == null) {
            return ListObjectResult.builder()
                    .resultCode(ResultCode.FAILURE)
                    .message("默认存储不存在")
                    .build();
        }
        return storage.listObject(request);
    }

    // ==================== 文件操作（指定存储） ====================

    /**
     * 上传文件到指定存储
     *
     * @param storageName 存储名称
     * @param request     上传请求
     * @return 上传结果
     */
    public PutObjectResult putObject(String storageName, PutObjectRequest request) {
        FileStorage storage = getStorage(storageName);
        if (storage == null) {
            return PutObjectResult.builder()
                    .resultCode(ResultCode.FAILURE)
                    .message("存储不存在: " + storageName)
                    .build();
        }
        return storage.putObject(request);
    }

    /**
     * 从指定存储获取文件
     *
     * @param storageName 存储名称
     * @param key         文件键
     * @return 获取结果
     */
    public GetObjectResult getObject(String storageName, String key) {
        FileStorage storage = getStorage(storageName);
        if (storage == null) {
            return GetObjectResult.builder()
                    .resultCode(ResultCode.FAILURE)
                    .message("存储不存在: " + storageName)
                    .build();
        }
        return storage.getObject(key);
    }

    /**
     * 从指定存储删除文件
     *
     * @param storageName 存储名称
     * @param key         文件键
     * @return 删除结果
     */
    public DeleteObjectResult deleteObject(String storageName, String key) {
        FileStorage storage = getStorage(storageName);
        if (storage == null) {
            return DeleteObjectResult.builder()
                    .resultCode(ResultCode.FAILURE)
                    .message("存储不存在: " + storageName)
                    .build();
        }
        return storage.deleteObject(key);
    }

    @Override
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
