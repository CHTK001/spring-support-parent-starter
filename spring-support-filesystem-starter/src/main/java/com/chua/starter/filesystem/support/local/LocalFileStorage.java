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
* 本地文件系统存储 SPI 实现（仅用于集成测试）。
*
* <p>将对象写入本地磁盘目录，提供最小化的 {@link com.chua.common.support.storage.FileStorage} 接口，
* 用于在没有真实云存储的情况下验证分片上传链路。</p>
*
* @since 4.0.0.42
* @author CH
 */
public class LocalFileStorage extends AbstractFileStorage {

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
        /**
        * 父。
        * @param setting setting
        */
        super(setting);
        String baseDir = Optional.ofNullable(System.getProperty("filesystem.temp.path"))
                .orElse(System.getProperty("java.io.tmpdir"))
                + "/filesystem-local-test/"
                + (bucket != null ? bucket : "default");
        this.rootPath = Paths.get(baseDir);
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            /**
            * runtime异常。
            * @param baseDir basedir
            * @param e e
            * @return runtime异常的结果
            */
            throw new RuntimeException("创建本地存储根目录失败: " + baseDir, e);
        }
    }

    @Override
    /** 创建multipartstorage */
    public MultipartStorage createMultipartStorage() {
        /**
    * 本地multipartstorage。
    * @return 本地multipartstorage的结果
    */
        return new LocalMultipartStorage();
    }

    @Override
    /** 放入对象 */
    public PutObjectResult putObject(PutObjectRequest request) {
        try {
            String key = resolveKey(request.getFilePath(), request.getFileName());
            Path target = rootPath.resolve(key);
            Files.createDirectories(target.getParent());
            // 键以路径分隔符结尾时语义为"创建目录"（与 S3 零字节目录占位对象的约定对齐），
            // 本地落盘为真实目录，不产生占位文件
            if (key.endsWith("/") || key.endsWith("\\")) {
                Files.createDirectories(target);
                return PutObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.SUCCESS)
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
    /** 获取对象 */
    public GetObjectResult getObject(String key) {
        Path target = rootPath.resolve(key);
        if (!Files.exists(target)) {
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
    /** 获取对象 */
    public GetObjectResult getObject(GetObjectRequest request) {
        return getObject(request.getKey());
    }

    @Override
    /** 删除对象 */
    public DeleteObjectResult deleteObject(String key) {
        try {
            Files.deleteIfExists(rootPath.resolve(key));
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
    /** exist对象 */
    public ExistObjectResult existObject(ExistObjectRequest request) {
        String key = request.getKey();
        boolean exists = key != null && Files.exists(rootPath.resolve(key));
        return ExistObjectResult.builder()
                .resultCode(ObjectResult.ResultCode.SUCCESS)
                .exists(exists)
                .build();
    }

    @Override
    /** 列表对象 */
    public ListObjectResult listObject(ListObjectRequest request) {
        try {
            List<Metadata> metadataList = new java.util.ArrayList<>();
            String dir = request.getFilePath();
            Path targetDir = dir == null || dir.isEmpty() ? rootPath : rootPath.resolve(dir);
            if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
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
                            } catch (IOException ignored) {
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
    * 解析钥匙
    *
    * @param filePath 文件路径
    * @param fileName 文件名称
    * @return resolve钥匙的结果
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
        /** Initiate */
        public MultipartPartResult initiate(PutObjectRequest request) {
            String uploadId = UUID.randomUUID().toString().replace("-", "");
            multipartMap.put(uploadId, new ConcurrentHashMap<>());
            return MultipartPartResult.builder()
                    .resultCode(ObjectResult.ResultCode.SUCCESS)
                    .uploadId(uploadId)
                    .build();
        }

        @Override
        /** uploadpart */
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
        /** 完成 */
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
        /** Abort */
        public DeleteObjectResult abort(String uploadId) {
            Map<Integer, Path> parts = multipartMap.remove(uploadId);
            if (parts != null) {
                parts.values().forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
            return DeleteObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.SUCCESS)
                    .build();
        }
    }
}
