package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.common.support.storage.request.ListObjectRequest;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.DeleteObjectResult;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ListObjectResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.starter.monitor.support.model.FileAssetContentVO;
import com.chua.starter.monitor.support.model.FileAssetItemVO;
import com.chua.starter.monitor.support.service.FileAssetService;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文件资产服务实现
 *
 * <p>存储后端来自容器内的 {@code FileStorage} Bean（本地磁盘由 {@code MonitorFileStorageConfig}
 * 注册，MinIO/S3 等由 filesystem 配置注册），与服务管理创建的 file-storage 节点共用同一实例，
 * 因此页面上的增删改与 HTTP 文件服务器看到的内容一致。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class FileAssetServiceImpl implements FileAssetService {

    /**
     * 默认存储桶（对外桶名；也是 {@code FileStorageTemplate.storageMap} 的键）
     */
    private static final String LOCAL_BUCKET = "default";

    /**
     * 单次列表默认上限
     */
    private static final int DEFAULT_LIMIT = 500;

    /**
     * 路径分隔符
     */
    private static final char SEPARATOR = '/';

    /**
     * 应用上下文，用于按名称解析 FileStorage Bean
     */
    private final ApplicationContext applicationContext;

    /**
     * 构造方法。
     *
     * @param applicationContext 应用上下文
     */
    public FileAssetServiceImpl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public List<FileAssetItemVO> list(String bucket, String path, int limit) {
        FileStorage storage = storage(bucket);
        String dir = normalize(path);
        ListObjectResult result = storage.listObject(ListObjectRequest.builder()
                .filePath(dir)
                .limit(limit > 0 ? limit : DEFAULT_LIMIT)
                .build());
        if (result == null || result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
            throw new IllegalStateException("读取目录失败: " + (result == null ? "无响应" : result.getMessage()));
        }
        List<FileAssetItemVO> items = new ArrayList<>();
        if (result.getMetadata() != null) {
            for (Metadata metadata : result.getMetadata()) {
                items.add(toItem(dir, metadata));
            }
        }
        return items;
    }

    @Override
    public FileAssetItemVO upload(String bucket, String path, String fileName, InputStream content, long size) {
        String name = requireFileName(fileName);
        FileStorage storage = storage(bucket);
        String dir = normalize(path);
        try (InputStream in = content) {
            PutObjectResult result = storage.putObject(PutObjectRequest.builder()
                    .filePath(dir)
                    .fileName(name)
                    .inputStream(in)
                    .build());
            if (result == null || result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
                throw new IllegalStateException("写入文件失败: " + (result == null ? "无响应" : result.getMessage()));
            }
            return new FileAssetItemVO(name, joinKey(dir, name), false,
                    size >= 0 ? size : 0, System.currentTimeMillis());
        } catch (IOException e) {
            throw new IllegalStateException("读取上传内容失败: " + e.getMessage(), e);
        }
    }

    @Override
    public FileAssetContentVO download(String bucket, String path) {
        String key = requireKey(path);
        FileStorage storage = storage(bucket);
        GetObjectResult result = storage.getObject(key);
        if (result == null || result.getResultCode() != ObjectResult.ResultCode.SUCCESS || result.getInputStream() == null) {
            closeQuietly(result);
            throw new IllegalArgumentException("文件不存在: " + key);
        }
        String fileName = key.substring(key.lastIndexOf(SEPARATOR) + 1);
        long length = result.getMetadata() != null ? result.getMetadata().getSize() : -1;
        return new FileAssetContentVO(fileName, guessContentType(fileName), length, result.getInputStream());
    }

    @Override
    public boolean delete(String bucket, String path) {
        String key = requireKey(path);
        FileStorage storage = storage(bucket);
        ListObjectResult children = storage.listObject(ListObjectRequest.builder().filePath(key).limit(1).build());
        if (children != null && children.getMetadata() != null && !children.getMetadata().isEmpty()) {
            throw new IllegalStateException("目录非空，请先删除其中的条目: " + key);
        }
        DeleteObjectResult result = storage.deleteObject(key);
        if (result == null || result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
            throw new IllegalStateException("删除失败: " + (result == null ? "无响应" : result.getMessage()));
        }
        return true;
    }

    @Override
    public List<String> buckets() {
        // 权威来源是 FileStorageTemplate.storageMap 的键（= 对外桶名 default），
        // 不能再用 getBeanNamesForType(FileStorage.class)：本地后端 Bean 名刻意与桶名解耦，
        // 用 Bean 名会把内部标识符（monitorLocalStorage）当成桶暴露出去。
        FileStorageTemplate template = applicationContext.getBeanProvider(FileStorageTemplate.class)
                .getIfAvailable();
        if (template != null) {
            List<String> names = new ArrayList<>(template.getStorageNames());
            if (!names.isEmpty()) {
                return names;
            }
        }
        // 兜底：没有模板（例如 file-storage 插件节点独立装配）时，按类型找后端，
        // 但对外仍以默认桶名呈现，避免把内部 Bean 名泄到页面上。
        if (!applicationContext.getBeansOfType(FileStorage.class).isEmpty()) {
            return List.of(LOCAL_BUCKET);
        }
        return List.of();
    }

    /**
     * 解析存储后端。
     *
     * <p>桶名是 {@code FileStorageTemplate.storageMap} 的键（对外桶名，默认 default），
     * 与容器内 Spring Bean 名无关——本地后端 Bean 名刻意不等于桶名，避免与 springdoc
     * 的 {@code default} 分组 Bean 撞名导致 storageMap 为空。</p>
     *
     * @param bucket 存储桶名称，为空取默认存储
     * @return 文件存储实例
     */
    private FileStorage storage(String bucket) {
        FileStorageTemplate template = applicationContext.getBeanProvider(FileStorageTemplate.class)
                .getIfAvailable();
        String name = (bucket == null || bucket.isBlank()) ? LOCAL_BUCKET : bucket.trim();
        if (template != null) {
            FileStorage storage = template.getStorage(name);
            if (storage != null) {
                return storage;
            }
            // 未显式指定桶时回退到模板里第一个可用后端
            if (bucket == null || bucket.isBlank()) {
                List<String> names = template.getStorageNames();
                if (!names.isEmpty()) {
                    return template.getStorage(names.get(0));
                }
            }
        }
        List<String> available = buckets();
        if (available.isEmpty()) {
            throw new IllegalStateException("未配置文件存储后端，请在「服务管理」创建 file-storage 节点或启用 filesystem 存储");
        }
        if (!available.contains(name)) {
            throw new IllegalArgumentException("存储桶不存在: " + name);
        }
        // 模板不可用时的兜底：按类型取第一个 FileStorage 实例
        // （orderedStream().findFirst() 避免多个 FileStorage 时 getIfAvailable() 抛 NoUniqueBeanDefinitionException）
        FileStorage fallback = applicationContext.getBeanProvider(FileStorage.class).orderedStream()
                .findFirst().orElse(null);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("未配置文件存储后端，请在「服务管理」创建 file-storage 节点或启用 filesystem 存储");
    }

    /**
     * 目录条目转 VO。
     *
     * @param dir      所在目录
     * @param metadata 存储元数据
     * @return 条目
     */
    private FileAssetItemVO toItem(String dir, Metadata metadata) {
        String name = metadata.getName() == null ? "" : metadata.getName();
        String stored = metadata.getPath();
        String base = stored == null || stored.isEmpty() ? dir : normalize(stored);
        return new FileAssetItemVO(name, joinKey(base, name), metadata.isDirectory(),
                metadata.getSize(), metadata.getLastModified());
    }

    /**
     * 规范化相对路径：去掉首尾斜杠、反斜杠改斜杠。
     *
     * @param raw 原始路径，可为空
     * @return 规范化路径，根目录返回空串
     */
    private String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim().replace('\\', SEPARATOR);
        List<String> segments = new ArrayList<>();
        for (String segment : value.split(SEPARATOR + "+")) {
            if (segment.isEmpty()) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("路径不合法: " + raw);
            }
            segments.add(segment);
        }
        return String.join(String.valueOf(SEPARATOR), segments);
    }

    /**
     * 规范化并校验对象路径。
     *
     * @param raw 原始路径
     * @return 对象键
     */
    private String requireKey(String raw) {
        String key = normalize(raw);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        return key;
    }

    /**
     * 校验文件名（去掉任何目录成分）。
     *
     * @param raw 原始文件名
     * @return 文件名
     */
    private String requireFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String value = raw.trim().replace('\\', SEPARATOR);
        value = value.substring(value.lastIndexOf(SEPARATOR) + 1);
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("文件名不合法: " + raw);
        }
        return value;
    }

    /**
     * 拼接目录与名称。
     *
     * @param dir  目录，可为空串
     * @param name 名称
     * @return 完整相对路径
     */
    private String joinKey(String dir, String name) {
        return dir == null || dir.isEmpty() ? name : dir + SEPARATOR + name;
    }

    /**
     * 按扩展名猜测内容类型。
     *
     * @param fileName 文件名
     * @return 内容类型
     */
    private String guessContentType(String fileName) {
        String guessed = URLConnection.guessContentTypeFromName(fileName);
        if (guessed != null) {
            return guessed;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            if ("md".equals(ext) || "log".equals(ext) || "txt".equals(ext)) {
                return "text/plain;charset=UTF-8";
            }
            if ("json".equals(ext)) {
                return "application/json;charset=UTF-8";
            }
            if ("csv".equals(ext)) {
                return "text/csv;charset=UTF-8";
            }
        }
        return "application/octet-stream";
    }

    /**
     * 关闭读取失败时残留的流。
     *
     * @param result 读取结果
     */
    private void closeQuietly(GetObjectResult result) {
        if (result == null || result.getInputStream() == null) {
            return;
        }
        try {
            result.getInputStream().close();
        } catch (IOException ignored) {
            // 关闭失败无需上报
        }
    }
}
