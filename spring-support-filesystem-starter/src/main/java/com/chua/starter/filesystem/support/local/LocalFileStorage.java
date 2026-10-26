package com.chua.starter.filesystem.support.local;

import com.chua.common.support.storage.AbstractFileStorage;
import com.chua.common.support.storage.MultipartStorage;
import com.chua.common.support.storage.PartETag;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.common.support.storage.request.ExistObjectRequest;
import com.chua.common.support.storage.request.GetObjectRequest;
import com.chua.common.support.storage.request.ListObjectRequest;
import com.chua.common.support.storage.request.MultipartUploadPartRequest;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.DeleteObjectResult;
import com.chua.common.support.storage.result.ExistObjectResult;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ListObjectResult;
import com.chua.common.support.storage.result.MultipartPartResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.common.support.storage.setting.BucketSetting;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地文件系统存储 SPI 实现。
 *
 * <p>将对象写入本地磁盘目录，提供 {@link com.chua.common.support.storage.FileStorage} 接口的
 * 无外部依赖落盘实现。配置了 {@code base-path} 时落盘到 {@code base-path/bucket}，
 * 未配置时退回系统临时目录（集成测试路径）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class LocalFileStorage extends AbstractFileStorage {

    /**
     * 日志
     */
    private static final System.Logger LOG = System.getLogger(LocalFileStorage.class.getName());

    /**
     * 临时目录系统属性键
     */
    private static final String TEMP_PATH_KEY = "filesystem.temp.path";

    /**
     * 附加配置键：本地存储根目录
     */
    private static final String BASE_PATH_KEY = "basePath";

    /**
     * 本地存储根目录
     */
    private final Path rootPath;

    /**
     * uploadid → 分片路径 映射
     */
    private final Map<String, Map<Integer, Path>> multipartMap = new ConcurrentHashMap<>();

    /**
     * 创建 本地文件storage 实例
     * @param setting setting
     */
    public LocalFileStorage(BucketSetting setting) {
        super(setting);
        this.rootPath = Paths.get(resolveRoot(setting)).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new RuntimeException("创建本地存储根目录失败: " + rootPath, e);
        }
    }

    /**
     * 解析存储根目录：显式 base-path 优先，缺省回退系统临时目录。
     *
     * @param setting 存储配置
     * @return 根目录绝对路径字符串
     */
    private String resolveRoot(BucketSetting setting) {
        Map<String, String> extra = setting.getExtraProperties();
        String base = extra == null ? null : extra.get(BASE_PATH_KEY);
        if (base != null && !base.isEmpty()) {
            return base + "/" + (bucket != null ? bucket : "default");
        }
        return Optional.ofNullable(System.getProperty(TEMP_PATH_KEY))
                .orElse(System.getProperty("java.io.tmpdir"))
                + "/filesystem-local-test/"
                + (bucket != null ? bucket : "default");
    }

    /**
     * 解析对象键并挡住目录穿越。
     *
     * @param key 对象键
     * @return 归一化后的绝对路径，越界返回 null
     */
    private Path resolveSafely(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        Path target = rootPath.resolve(key).normalize();
        return target.startsWith(rootPath) ? target : null;
    }

    @Override
    /**
     * 创建multipartstorage
    */
    public MultipartStorage createMultipartStorage() {
        return new LocalMultipartStorage();
    }

    @Override
    /**
     * 放入对象
    */
    public PutObjectResult putObject(PutObjectRequest request) {
        try {
            String key = resolveKey(request.getFilePath(), request.getFileName());
            Path target = resolveSafely(key);
            if (target == null) {
                return PutObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("非法对象键: " + key)
                        .build();
            }
            Files.createDirectories(target.getParent());
            // 键以路径分隔符结尾时语义为"创建目录"（与 S3 零字节目录占位对象的约定对齐），
            // 本地落盘为真实目录，不产生占位文件
            if (key.endsWith("/") || key.endsWith("\\")) {
                Files.createDirectories(target);
                return PutObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.SUCCESS)
                        .key(key)
                        .url(target.toUri().toString())
                        .message("OK")
                        .build();
            }
            if (request.getContent() != null) {
                Files.write(target, request.getContent());
            } else if (request.getInputStream() != null) {
                Files.copy(request.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                return PutObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("内容为空")
                        .build();
            }
            return PutObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.SUCCESS)
                    .key(key)
                    .url(target.toUri().toString())
                    .message("OK")
                    .build();
        } catch (IOException e) {
            return PutObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("写入失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    /**
     * 获取对象
    */
    public GetObjectResult getObject(String key) {
        Path target = resolveSafely(key);
        if (target == null || !Files.exists(target)) {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("对象不存在: " + key)
                    .build();
        }
        try {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.SUCCESS)
                    .inputStream(Files.newInputStream(target))
                    .build();
        } catch (IOException e) {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("读取失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    /**
     * 获取对象
    */
    public GetObjectResult getObject(GetObjectRequest request) {
        return getObject(request.getKey());
    }

    @Override
    /**
     * 删除对象
    */
    public DeleteObjectResult deleteObject(String key) {
        try {
            Path target = resolveSafely(key);
            if (target == null) {
                return DeleteObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("非法对象键: " + key)
                        .build();
            }
            Files.deleteIfExists(target);
            return DeleteObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.SUCCESS)
                    .build();
        } catch (IOException e) {
            return DeleteObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message(e.getMessage())
                    .build();
        }
    }

    @Override
    /**
     * exist对象
    */
    public ExistObjectResult existObject(ExistObjectRequest request) {
        String key = request.getKey();
        Path target = resolveSafely(key);
        boolean exists = target != null && Files.exists(target);
        return ExistObjectResult.builder()
                .resultCode(ObjectResult.ResultCode.SUCCESS)
                .exists(exists)
                .build();
    }

    @Override
    /**
     * 列表对象
    */
    public ListObjectResult listObject(ListObjectRequest request) {
        try {
            List<Metadata> metadataList = new java.util.ArrayList<>();
            String dir = request.getFilePath();
            Path targetDir = dir == null || dir.isEmpty() ? rootPath : resolveSafely(dir);
            if (targetDir == null || !Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
                return ListObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.SUCCESS)
                        .metadata(metadataList)
                        .build();
            }
            int limit = request.getLimit() > 0 ? request.getLimit() : 10;
            /**
             * 尝试。
             * @param Files.list(targetDir 文件.列表(Targetdir
             */
            try (java.util.stream.Stream<Path> stream = Files.list(targetDir)) {
                stream.sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                        .limit(limit)
                        .forEach(path -> {
                            try {
                                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                                String name = path.getFileName().toString();
                                metadataList.add(Metadata.builder()
                                        .name(name)
                                        .path(dir == null ? "" : dir)
                                        .size(attrs.isDirectory() ? 0 : attrs.size())
                                        .directory(attrs.isDirectory())
                                        .lastModified(attrs.lastModifiedTime().toInstant().toEpochMilli())
                                        .build());
                            } catch (IOException e) {
                                LOG.log(System.Logger.Level.DEBUG,
                                        "[storage] 关闭文件通道失败，已忽略: " + e.getMessage(), e);
                            }
                        });
            }
            return ListObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.SUCCESS)
                    .metadata(metadataList)
                    .build();
        } catch (IOException e) {
            return ListObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message(e.getMessage())
                    .build();
        }
    }

    /**
     * 解析Key
     *
     * @param filePath 文件路径
     * @param fileName 文件名称
     * @return resolveKey的结果
     */
    private String resolveKey(String filePath, String fileName) {
        if (filePath == null || filePath.isEmpty()) {
            return fileName;
        }
        return filePath + "/" + fileName;
    }

    /**
     * 本地分片上传会话。
     * @author CH
     * @since 4.0.0
     */
    private class LocalMultipartStorage implements MultipartStorage {

        @Override
        /**
         * Initiate
        */
        public MultipartPartResult initiate(PutObjectRequest request) {
            String uploadId = UUID.randomUUID().toString().replace("-", "");
            multipartMap.put(uploadId, new ConcurrentHashMap<>());
            return MultipartPartResult.builder()
                    .resultCode(ObjectResult.ResultCode.SUCCESS)
                    .uploadId(uploadId)
                    .build();
        }

        @Override
        /**
         * uploadpart
        */
        public MultipartPartResult uploadPart(MultipartUploadPartRequest request) {
            String uploadId = request.getUploadId();
            Map<Integer, Path> parts = multipartMap.get(uploadId);
            if (parts == null) {
                return MultipartPartResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("uploadId 不存在: " + uploadId)
                        .build();
            }
            Path partPath = rootPath.resolve(uploadId + "_" + request.getPartNumber());
            try {
                Files.write(partPath, request.getContent());
                parts.put(request.getPartNumber(), partPath);
                return MultipartPartResult.builder()
                        .resultCode(ObjectResult.ResultCode.SUCCESS)
                        .partSize((long) request.getContent().length)
                        .etag("etag-" + request.getPartNumber())
                        .build();
            } catch (IOException e) {
                return MultipartPartResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("写入分片失败: " + e.getMessage())
                        .build();
            }
        }

        @Override
        /**
         * 完成
        */
        public PutObjectResult complete(String uploadId, List<PartETag> parts) {
            Map<Integer, Path> partMap = multipartMap.remove(uploadId);
            if (partMap == null) {
                return PutObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("uploadId 不存在: " + uploadId)
                        .build();
            }
            Path finalFile = rootPath.resolve(uploadId + "_final");
            /**
             * 尝试。
             * @param Files.newOutputStream(finalFile 文件.新输出流(最终文件
             */
            try (OutputStream out = Files.newOutputStream(finalFile)) {
                for (int i = 1; i <= parts.size(); i++) {
                    Path p = partMap.get(i);
                    if (p == null) {
                        return PutObjectResult.builder()
                                .resultCode(ObjectResult.ResultCode.FAILURE)
                                .message("分片缺失: " + i)
                                .build();
                    }
                    Files.copy(p, out);
                }
                return PutObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.SUCCESS)
                        .url(finalFile.toUri().toString())
                        .message("OK")
                        .build();
            } catch (IOException e) {
                return PutObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("合并失败: " + e.getMessage())
                        .build();
            }
        }

        @Override
        /**
         * Abort
        */
        public DeleteObjectResult abort(String uploadId) {
            Map<Integer, Path> parts = multipartMap.remove(uploadId);
            if (parts != null) {
                parts.values().forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        LOG.log(System.Logger.Level.DEBUG,
                                "[storage] 删除文件失败，已忽略: " + e.getMessage(), e);
                    }
                });
            }
            return DeleteObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.SUCCESS)
                    .build();
        }
    }
}
