// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import java.io.InputStream;
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
     * 取本地存储根目录（对象 key 相对此目录）。
     *
     * <p>发布管理「从主机文件选升级包」需要把 key 还原成主机上的绝对路径，
     * 再交给 SFTP 投放，因此必须暴露根目录，而不是让调用方猜 base-path 拼接规则。</p>
     *
     * @return 根目录绝对路径
     */
    public Path getRootPath() {
        return rootPath;
    }

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
     * @return 返回值
    */
    public MultipartStorage createMultipartStorage() {
        return new LocalMultipartStorage();
    }

    @Override
    /**
     * 放入对象
     * @param request 参数
     * @return 返回值
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
            } else if (request.getFile() != null) {
                // 分片合并路径：builder.file(mergedFile) 只设 File，不设 content/inputStream，
                // 这里必须显式读文件字节，否则一律抵达「内容为空」分支
                Files.copy(request.getFile().toPath(), target, StandardCopyOption.REPLACE_EXISTING);
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
     * @param key 字符串
     * @return 返回值
    */
    public GetObjectResult getObject(String key) {
        return openObject(key, resolveSafely(key), 0L, -1L);
    }

    @Override
    /**
     * 获取对象
     * @param request 参数
     * @return 返回值
    */
    public GetObjectResult getObject(GetObjectRequest request) {
        String key = request.getKey();
        return openObject(key, resolveSafely(key), Math.max(0L, request.getOffset()), request.getLength());
    }

    /**
     * 打开对象并按偏移/长度裁剪输入流。
     *
     * <p>必须回填 {@link Metadata}（尤其是 {@code size} 与 {@code lastModified}）：
     * 文件服务过滤器的 HTTP Range 快速通道靠 {@code size} 生成
     * {@code Content-Range} / 判断 416，{@code lastModified} 用于
     * {@code Last-Modified} 与 {@code If-Range} 校验。此前的实现只给输入流、
     * 不给元数据，导致快速通道在 {@code totalSize<=0} 处直接放弃，Range 全部
     * 落回整读链路（越界不返回 416、If-Range 失效、206 缺 ETag）。</p>
     *
     * @param key    对象键
     * @param target 解析后的绝对路径（越界为 null）
     * @param offset 起始偏移，≤0 表示从头
     * @param length 读取长度，&lt;0 表示读到末尾
     * @return 获取结果
     */
    private GetObjectResult openObject(String key, Path target, long offset, long length) {
        if (target == null || !Files.exists(target)) {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("对象不存在: " + key)
                    .build();
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
            if (attrs.isDirectory()) {
                return GetObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("对象是目录: " + key)
                        .build();
            }
            long size = attrs.size();
            if (offset > 0L && offset >= size) {
                  // 越界：过滤器据此回 416，而不是凭空给一个 0 字节的 206
                return GetObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("偏移超出文件长度: " + offset + " >= " + size)
                        .build();
            }
            InputStream inputStream;
            try {
                inputStream = applyRange(Files.newInputStream(target), offset, length);
            } catch (IOException e) {
                return GetObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.FAILURE)
                        .message("读取失败: " + e.getMessage())
                        .build();
            }
            String name = target.getFileName() == null ? fileNameOf(key) : target.getFileName().toString();
            String suffix = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.SUCCESS)
                    .inputStream(inputStream)
                    .metadata(Metadata.builder()
                            .name(name)
                            .path(parentOf(key))
                            .size(size)
                            .suffix(suffix)
                            .directory(false)
                            .lastModified(attrs.lastModifiedTime().toInstant().toEpochMilli())
                            .build())
                    .build();
        } catch (IOException e) {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("读取失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 本地文件系统原生支持按偏移/长度读取（{@link #applyRange} 走 skip + 长度上限，
     * 本地文件 skip 等价于 lseek，不需要白读前缀）。
     *
     * @return 恒为 true
     */
    @Override
    public boolean supportsRange() {
        return true;
    }

    /**
     * 取对象键的末段文件名。
     *
     * @param key 对象键
     * @return 文件名
     */
    private String fileNameOf(String key) {
        return key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
    }

    /**
     * 取对象键的父目录部分。
     *
     * @param key 对象键
     * @return 父目录（根下对象返回空串）
     */
    private String parentOf(String key) {
        int index = key.lastIndexOf('/');
        return index < 0 ? "" : key.substring(0, index);
    }

    @Override
    /**
     * 删除对象
     * @param key 字符串
     * @return 返回值
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
     * @param request 参数
     * @return 返回值
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
     * @param request 参数
     * @return 返回值
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
                        .total(0)
                        .build();
            }
            long limit = request.getLimit() > 0 ? request.getLimit() : 10;
            long offset = Math.max(0, request.getOffset());
            /**
             * 尝试。
             * @param Files.list(targetDir 文件.列表(Targetdir
             */
            try (java.util.stream.Stream<Path> stream = Files.list(targetDir)) {
                // 先只枚举子项并按名称排序（不触碰属性），再按 offset/limit 切片；
                // 只有切片内的条目才会做 readAttributes。
                // 瓦片图这类超大规模目录，逐项 stat 是卡死根因（一次读属性一次磁盘 IO），
                // 切片后 stat 次数与「本页条数」成正比，与目录总量无关。
                List<Path> sorted = stream
                        .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                        .collect(java.util.stream.Collectors.toList());
                long total = sorted.size();
                long from = Math.min(offset, total);
                long to = Math.min(offset + limit, total);
                for (int i = (int) from; i < (int) to; i++) {
                    Path path = sorted.get(i);
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
                }
                return ListObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.SUCCESS)
                        .metadata(metadataList)
                        .total(total)
                        .build();
            }
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
         * @param request 参数
         * @return 返回值
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
         * @param request 参数
         * @return 返回值
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
         * @param parts 参数
         * @param uploadId 字符串
         * @return 返回值
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
         * @param uploadId 字符串
         * @return 返回值
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
