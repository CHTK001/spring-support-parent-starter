package com.chua.starter.server.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.common.support.storage.request.ExistObjectRequest;
import com.chua.common.support.storage.request.ListObjectRequest;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.DeleteObjectResult;
import com.chua.common.support.storage.result.ExistObjectResult;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ListObjectResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.starter.filesystem.support.local.LocalFileStorage;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import com.chua.starter.server.support.model.vo.HostFileConfigVO;
import com.chua.starter.server.support.model.vo.HostFileDeleteVO;
import com.chua.starter.server.support.model.vo.HostFileDigestVO;
import com.chua.starter.server.support.model.vo.HostFileEntryVO;
import com.chua.starter.server.support.model.vo.HostFilePageVO;
import com.chua.starter.server.support.service.HostFileService;
import com.chua.starter.server.support.util.HostFilePaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 主机文件服务实现。
 *
 * <p>语义分级：</p>
 * <ul>
 *   <li><b>文件操作</b>（列目录 / 上传 / 下载 / 删除文件）对所有存储后端成立，走
 *       {@link FileStorage} SPI；</li>
 *   <li><b>本机操作</b>（MD5、重命名、建目录、递归删目录、主机绝对路径）只在
 *       {@link LocalFileStorage} 上成立 —— 对象存储没有稳定的目录对象语义，
 *       假装支持只会让前端出现「点了没反应」的按钮。</li>
 * </ul>
 *
 * <p>目录扫描上限 {@link #MAX_SCAN}：本地存储列目录会逐个 stat 切片内的条目，
 * 一次扫全量大目录会把磁盘打满，所以只扫前若干条并把 {@code truncated}
 * 如实回给前端。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Slf4j
@Service
public class HostFileServiceImpl implements HostFileService {

    /**
     * 单次最多扫描的条目数
     */
    private static final int MAX_SCAN = 2000;

    /**
     * 默认每页条数
     */
    private static final int DEFAULT_LIMIT = 200;

    /**
     * 单页最大条数
     */
    private static final int MAX_LIMIT = 500;

    /**
     * 排序字段：名称
     */
    private static final String SORT_NAME = "name";

    /**
     * 排序字段：大小
     */
    private static final String SORT_SIZE = "size";

    /**
     * 排序字段：修改时间
     */
    private static final String SORT_TIME = "time";

    /**
     * 排序方向：降序
     */
    private static final String ORDER_DESC = "desc";

    /**
     * 文本预览读取上限（256KB）
     */
    private static final int TEXT_PREVIEW_BYTES = 256 * 1024;

    /**
     * MD5 读取缓冲区
     */
    private static final int DIGEST_BUFFER_BYTES = 64 * 1024;

    /**
     * 未启用文件存储 / 提供者不存在时的统一提示
     */
    private static final String DISABLED_MESSAGE =
            "文件存储未启用或存储提供者不存在，请检查 plugin.filesystem.enable 与 providers 配置";

    /**
     * 存储模板（宿主未装配文件存储时为 null）
     */
    private final ObjectProvider<FileStorageTemplate> templateProvider;

    /**
     * MD5 台账（列表直接回显指纹，不再每次进目录都重算）
     */
    private final HostFileDigestRepository digestRepository;

    /**
     * 构造。
     *
     * @param templateProvider 存储模板提供者
     * @param digestRepository MD5 台账
     */
    public HostFileServiceImpl(ObjectProvider<FileStorageTemplate> templateProvider,
                               HostFileDigestRepository digestRepository) {
        this.templateProvider = templateProvider;
        this.digestRepository = digestRepository;
    }

    @Override
    public ReturnResult<HostFileConfigVO> config(String bucket) {
        FileStorageTemplate template = template();
        if (template == null) {
            return ReturnResult.ok(new HostFileConfigVO(false, "", "", false, List.of(), ""));
        }
        String name = resolveName(template, bucket);
        FileStorage storage = name.isEmpty() ? null : storageOf(template, name);
        if (storage == null) {
            return ReturnResult.ok(new HostFileConfigVO(false, name, "", false, template.getStorageNames(), name));
        }
        Path root = localRoot(storage);
        return ReturnResult.ok(new HostFileConfigVO(true, name, root == null ? "" : root.toString(),
                root != null, template.getStorageNames(), template.getDefaultStorageName()));
    }

    @Override
    public ReturnResult<HostFilePageVO> list(String bucket, String path, String keyword, String sort,
                                             String order, int limit, int offset) {
        FileStorage storage = requireStorage(bucket);
        if (storage == null) {
            return ReturnResult.error(DISABLED_MESSAGE);
        }
        String dir = HostFilePaths.normalizeDir(path);
        if (dir == null) {
            return ReturnResult.illegal("目录路径不合法");
        }
        try {
            ListObjectResult result = storage.listObject(ListObjectRequest.builder()
                    .filePath(dir)
                    .limit(MAX_SCAN)
                    .offset(0)
                    .build());
            List<HostFileEntryVO> all = toEntries(storage, dir, result);
            String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
            List<HostFileEntryVO> matched = new ArrayList<>(all.size());
            for (HostFileEntryVO entry : all) {
                if (needle.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(needle)) {
                    matched.add(entry);
                }
            }
            matched.sort(comparator(sort, order));
            int pageSize = clampLimit(limit);
            int from = Math.min(Math.max(0, offset), matched.size());
            int to = Math.min(from + pageSize, matched.size());
            long rawTotal = result == null ? all.size() : Math.max(result.getTotal(), all.size());
            boolean truncated = rawTotal > all.size() || matched.size() > to;
            List<HostFileEntryVO> page = matched.subList(from, to);
            // 指纹一次性批量回填：列表要直接显示 MD5，逐条查库会把目录页变成 N 次往返
            return ReturnResult.ok(new HostFilePageVO(dir, keyword == null ? "" : keyword.trim(),
                    attachDigests(bucket, page), matched.size(), all.size(), truncated));
        } catch (RuntimeException e) {
            log.error("[主机文件] 列目录失败: path={}", dir, e);
            return ReturnResult.error("列目录失败: " + messageOf(e));
        }
    }

    @Override
    public ReturnResult<HostFileEntryVO> mkdir(String bucket, String path, String name) {
        FileStorage storage = requireStorage(bucket);
        if (storage == null) {
            return ReturnResult.error(DISABLED_MESSAGE);
        }
        String dir = HostFilePaths.normalizeDir(path);
        String key = HostFilePaths.child(dir, name);
        if (dir == null || key == null) {
            return ReturnResult.illegal("目录名不合法");
        }
        if (exists(storage, key)) {
            return ReturnResult.illegal("同名文件或目录已存在");
        }
        try {
            // 以「key 以分隔符结尾」表达建目录：本地实现落真实目录，对象存储落零字节占位对象
            PutObjectResult result = storage.putObject(PutObjectRequest.builder()
                    .filePath(dir)
                    .fileName(name.trim() + HostFilePaths.SEPARATOR)
                    .content(new byte[0])
                    .build());
            if (result == null || ObjectResult.ResultCode.FAILURE == result.getResultCode()) {
                return ReturnResult.error(result == null ? "创建目录失败" : messageOf(result.getMessage()));
            }
            return ReturnResult.ok(entryOf(storage, key, name.trim(), true, 0L, System.currentTimeMillis()));
        } catch (RuntimeException e) {
            return ReturnResult.error("创建目录失败: " + messageOf(e));
        }
    }

    @Override
    public ReturnResult<HostFileEntryVO> rename(String bucket, String key, String name) {
        String normalized = HostFilePaths.normalizeKey(key);
        if (normalized == null || normalized.isEmpty()) {
            return ReturnResult.illegal("文件 key 不合法");
        }
        if (!HostFilePaths.isSafeName(name)) {
            return ReturnResult.illegal("新名称不合法");
        }
        return moveTo(bucket, normalized, HostFilePaths.parentOf(normalized), name.trim());
    }

    @Override
    public ReturnResult<HostFileEntryVO> move(String bucket, String key, String targetDir) {
        String normalized = HostFilePaths.normalizeKey(key);
        String dir = HostFilePaths.normalizeKey(targetDir);
        if (normalized == null || normalized.isEmpty() || dir == null) {
            return ReturnResult.illegal("路径不合法");
        }
        String name = HostFilePaths.nameOf(normalized);
        if (!HostFilePaths.isSafeName(name)) {
            return ReturnResult.illegal("文件 key 不合法");
        }
        return moveTo(bucket, normalized, dir, name);
    }

    @Override
    public ReturnResult<Boolean> delete(String bucket, String key) {
        FileStorage storage = requireStorage(bucket);
        if (storage == null) {
            return ReturnResult.error(DISABLED_MESSAGE);
        }
        String normalized = HostFilePaths.normalizeKey(key);
        if (normalized == null || normalized.isEmpty()) {
            return ReturnResult.illegal("文件 key 不合法");
        }
        try {
            Path root = localRoot(storage);
            if (root == null) {
                DeleteObjectResult result = storage.deleteObject(normalized);
                boolean ok = result != null && ObjectResult.ResultCode.SUCCESS == result.getResultCode();
                return ok ? ReturnResult.ok(Boolean.TRUE) : ReturnResult.error(messageOf(result));
            }
            Path target = resolveLocal(root, normalized);
            if (target == null || !Files.exists(target)) {
                return ReturnResult.error("文件不存在: " + normalized);
            }
            deleteLocal(target);
            digestRepository.delete(resolveDigestBucket(bucket), normalized);
            return ReturnResult.ok(Boolean.TRUE);
        } catch (IOException e) {
            return ReturnResult.error("删除失败: " + messageOf(e));
        } catch (RuntimeException e) {
            return ReturnResult.error("删除失败: " + messageOf(e));
        }
    }

    @Override
    public ReturnResult<HostFileDeleteVO> deleteBatch(String bucket, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return ReturnResult.illegal("请选择要删除的条目");
        }
        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        for (String key : keys) {
            ReturnResult<Boolean> result = delete(bucket, key);
            if (result.isOk() && Boolean.TRUE.equals(result.getData())) {
                succeeded++;
            } else {
                failures.add(key + ": " + result.getMsg());
            }
        }
        return ReturnResult.ok(new HostFileDeleteVO(succeeded, failures.size(), failures));
    }

    @Override
    public ReturnResult<HostFileDigestVO> md5(String bucket, String key) {
        FileStorage storage = requireStorage(bucket);
        if (storage == null) {
            return ReturnResult.error(DISABLED_MESSAGE);
        }
        Path root = localRoot(storage);
        if (root == null) {
            return ReturnResult.error("仅本地文件存储支持计算 MD5");
        }
        String normalized = HostFilePaths.normalizeKey(key);
        Path file = normalized == null ? null : resolveLocal(root, normalized);
        if (file == null || !Files.isRegularFile(file)) {
            return ReturnResult.error("文件不存在: " + key);
        }
        long start = System.currentTimeMillis();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[DIGEST_BUFFER_BYTES];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            String hex = HexFormat.of().formatHex(digest.digest());
            // 按需计算也顺手入库（来源 SERVER）：同文件再点一次就命中台账，
            // 且列表刷新后直接显示，不用等下一次上传
            digestRepository.save(resolveDigestBucket(bucket), normalized, hex, Files.size(file), "SERVER");
            return ReturnResult.ok(new HostFileDigestVO(hex, Files.size(file), System.currentTimeMillis() - start));
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("[主机文件] 计算 MD5 失败: key={}", normalized, e);
            return ReturnResult.error("计算 MD5 失败: " + messageOf(e));
        }
    }

    @Override
    public GetObjectResult open(String bucket, String key) {
        FileStorage storage = requireStorage(bucket);
        if (storage == null) {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message(DISABLED_MESSAGE)
                    .build();
        }
        String normalized = HostFilePaths.normalizeKey(key);
        if (normalized == null || normalized.isEmpty()) {
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message("文件 key 不合法")
                    .build();
        }
        try {
            return storage.getObject(normalized);
        } catch (RuntimeException e) {
            log.error("[主机文件] 读取失败: key={}", normalized, e);
            return GetObjectResult.builder()
                    .resultCode(ObjectResult.ResultCode.FAILURE)
                    .message(messageOf(e))
                    .build();
        }
    }

    @Override
    public ReturnResult<String> readText(String bucket, String key) {
        String normalized = HostFilePaths.normalizeKey(key);
        if (normalized == null || !HostFilePaths.isPreviewableText(HostFilePaths.nameOf(normalized))) {
            return ReturnResult.illegal("该类型不支持在线预览");
        }
        GetObjectResult result = open(bucket, key);
        if (result == null || ObjectResult.ResultCode.FAILURE == result.getResultCode()
                || result.getInputStream() == null) {
            return ReturnResult.error(result == null ? "读取失败" : messageOf(result.getMessage()));
        }
        try (InputStream in = result.getInputStream()) {
            byte[] head = in.readNBytes(TEXT_PREVIEW_BYTES);
            String text = new String(head, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            return ReturnResult.ok(text);
        } catch (IOException e) {
            return ReturnResult.error("读取失败: " + messageOf(e));
        }
    }

    /* ==================== 内部实现 ==================== */

    /**
     * 通用的「改名 / 移动」：同目录改名与跨目录移动共用一套落点校验。
     *
     * @param bucket    存储提供者名
     * @param key       源 key
     * @param targetDir 目标目录 key
     * @param name      目标名称
     * @return 移动后的条目
     */
    private ReturnResult<HostFileEntryVO> moveTo(String bucket, String key, String targetDir, String name) {
        FileStorage storage = requireStorage(bucket);
        if (storage == null) {
            return ReturnResult.error(DISABLED_MESSAGE);
        }
        String targetKey = HostFilePaths.child(targetDir, name);
        if (targetKey == null) {
            return ReturnResult.illegal("名称不合法");
        }
        if (targetKey.equals(key)) {
            return ReturnResult.ok(entryOf(storage, key, name, false, 0L, System.currentTimeMillis()));
        }
        if (exists(storage, targetKey)) {
            return ReturnResult.illegal("目标位置已存在同名条目");
        }
        try {
            Path root = localRoot(storage);
            if (root != null) {
                return moveLocal(bucket, storage, root, key, targetKey);
            }
            return moveObject(bucket, storage, key, targetKey);
        } catch (IOException e) {
            return ReturnResult.error("移动失败: " + messageOf(e));
        } catch (RuntimeException e) {
            return ReturnResult.error("移动失败: " + messageOf(e));
        }
    }

    /**
     * 本地存储上的移动：目录与文件都走 NIO，目录移动只改父目录项，不做递归拷贝。
     *
     * @param bucket    存储提供者名（台账分区）
     * @param storage   存储
     * @param root      存储根
     * @param key       源 key
     * @param targetKey 目标 key
     * @return 移动后的条目
     * @throws IOException 移动失败
     */
    private ReturnResult<HostFileEntryVO> moveLocal(String bucket, FileStorage storage, Path root, String key,
                                                    String targetKey) throws IOException {
        Path source = resolveLocal(root, key);
        if (source == null || !Files.exists(source)) {
            return ReturnResult.error("文件不存在: " + key);
        }
        Path target = resolveLocal(root, targetKey);
        if (target == null) {
            return ReturnResult.illegal("目标路径不合法");
        }
        boolean directory = Files.isDirectory(source);
        if (directory && target.startsWith(source)) {
            return ReturnResult.illegal("不能把目录移动到它自己的子目录");
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.move(source, target);
        digestRepository.relocate(resolveDigestBucket(bucket), key, targetKey);
        return ReturnResult.ok(entryOf(storage, targetKey, HostFilePaths.nameOf(targetKey), directory, 0L,
                System.currentTimeMillis()));
    }

    /**
     * 对象存储上的移动：只支持文件（流式复制后删除源），目录明确拒绝。
     *
     * @param bucket    存储提供者名（台账分区）
     * @param storage   存储
     * @param key       源 key
     * @param targetKey 目标 key
     * @return 移动后的条目
     */
    private ReturnResult<HostFileEntryVO> moveObject(String bucket, FileStorage storage, String key,
                                                     String targetKey) {
        GetObjectResult source = storage.getObject(key);
        if (source == null || ObjectResult.ResultCode.FAILURE == source.getResultCode()
                || source.getInputStream() == null) {
            return ReturnResult.error("文件不存在: " + key);
        }
        String parent = HostFilePaths.parentOf(targetKey);
        String name = HostFilePaths.nameOf(targetKey);
        try (InputStream in = source.getInputStream()) {
            PutObjectResult put = storage.putObject(PutObjectRequest.builder()
                    .filePath(parent)
                    .fileName(name)
                    .inputStream(in)
                    .build());
            if (put == null || ObjectResult.ResultCode.FAILURE == put.getResultCode()) {
                return ReturnResult.error("移动失败: " + messageOf(put == null ? null : put.getMessage()));
            }
        } catch (IOException e) {
            return ReturnResult.error("移动失败: " + messageOf(e));
        }
        storage.deleteObject(key);
        digestRepository.relocate(resolveDigestBucket(bucket), key, targetKey);
        return ReturnResult.ok(entryOf(storage, targetKey, name, false, 0L, System.currentTimeMillis()));
    }

    /**
     * 给目录页条目回填已登记的 MD5。
     *
     * @param bucket 存储提供者名
     * @param page   当前页条目
     * @return 回填后的条目（目录与无记录的文件 md5 为 null）
     */
    private List<HostFileEntryVO> attachDigests(String bucket, List<HostFileEntryVO> page) {
        List<String> keys = new ArrayList<>();
        for (HostFileEntryVO item : page) {
            if (!item.directory()) {
                keys.add(item.key());
            }
        }
        if (keys.isEmpty()) {
            return page;
        }
        Map<String, String> digests = digestRepository.find(resolveDigestBucket(bucket), keys);
        if (digests.isEmpty()) {
            return page;
        }
        List<HostFileEntryVO> rows = new ArrayList<>(page.size());
        for (HostFileEntryVO item : page) {
            String md5 = item.directory() ? null : digests.get(item.key());
            rows.add(new HostFileEntryVO(item.name(), item.key(), item.parent(), item.size(), item.directory(),
                    item.lastModified(), item.kind(), item.ext(), item.hostPath(), md5));
        }
        return rows;
    }

    /**
     * 台账分区名：与 HostFileDigestServiceImpl 保持同一口径（空桶收敛到 {@code _default}）。
     *
     * @param bucket 请求里的提供者名
     * @return 台账分区名
     */
    private String resolveDigestBucket(String bucket) {
        return bucket == null || bucket.isBlank() ? "_default" : bucket.trim();
    }

    /**
     * 元数据转条目。
     *
     * @param storage 存储
     * @param dir     目录 key
     * @param result  列目录结果
     * @return 条目列表
     */
    private List<HostFileEntryVO> toEntries(FileStorage storage, String dir, ListObjectResult result) {
        List<HostFileEntryVO> entries = new ArrayList<>();
        if (result == null || result.getMetadata() == null) {
            return entries;
        }
        for (Metadata meta : result.getMetadata()) {
            String name = meta.getName();
            String key = HostFilePaths.normalizeKey(dir + name);
            if (key == null || key.isEmpty()) {
                continue;
            }
            entries.add(entryOf(storage, key, name, meta.isDirectory(), meta.getSize(), meta.getLastModified()));
        }
        return entries;
    }

    /**
     * 组单条条目。
     *
     * @param storage     存储
     * @param key         条目 key
     * @param name        名称
     * @param directory   是否目录
     * @param size        字节数
     * @param lastModified 修改时间
     * @return 条目
     */
    private HostFileEntryVO entryOf(FileStorage storage, String key, String name, boolean directory,
                                    long size, long lastModified) {
        Path root = localRoot(storage);
        String hostPath = null;
        if (root != null && !directory) {
            Path file = resolveLocal(root, key);
            hostPath = file == null ? null : file.toString();
        }
        return new HostFileEntryVO(name, key, HostFilePaths.parentOf(key), directory ? 0L : size,
                directory, lastModified, HostFilePaths.kindOf(name, directory), HostFilePaths.extOf(name), hostPath,
                null);
    }

    /**
     * 排序器：目录恒在前；同组内按字段排序。
     *
     * @param sort  字段
     * @param order 方向
     * @return 比较器
     */
    private Comparator<HostFileEntryVO> comparator(String sort, String order) {
        Comparator<HostFileEntryVO> byField;
        if (SORT_SIZE.equalsIgnoreCase(sort)) {
            byField = Comparator.comparingLong(HostFileEntryVO::size);
        } else if (SORT_TIME.equalsIgnoreCase(sort)) {
            byField = Comparator.comparingLong(HostFileEntryVO::lastModified);
        } else if (SORT_NAME.equalsIgnoreCase(sort)) {
            byField = Comparator.comparing(HostFileEntryVO::name, String.CASE_INSENSITIVE_ORDER);
        } else {
            byField = Comparator.comparing(HostFileEntryVO::name, String.CASE_INSENSITIVE_ORDER);
        }
        if (ORDER_DESC.equalsIgnoreCase(order)) {
            byField = byField.reversed();
        }
        return Comparator.comparing(HostFileEntryVO::directory).reversed().thenComparing(byField);
    }

    /**
     * 判定条目是否存在。
     *
     * @param storage 存储
     * @param key     条目 key
     * @return 存在返回 true
     */
    private boolean exists(FileStorage storage, String key) {
        Path root = localRoot(storage);
        if (root != null) {
            Path target = resolveLocal(root, key);
            return target != null && Files.exists(target);
        }
        String parent = HostFilePaths.parentOf(key);
        String name = HostFilePaths.nameOf(key);
        try {
            ExistObjectResult result = storage.existObject(ExistObjectRequest.builder()
                    .filePath(parent)
                    .fileName(name)
                    .build());
            return result != null && result.isExists();
        } catch (RuntimeException e) {
            log.debug("[主机文件] 存在性检查失败，按不存在处理: key={} - {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 递归删除本地文件 / 目录。
     *
     * @param target 绝对路径
     * @throws IOException 删除失败
     */
    private void deleteLocal(Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            Files.deleteIfExists(target);
            return;
        }
        List<Path> paths;
        try (var stream = Files.walk(target)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    /**
     * 解析本机绝对路径并挡住越界。
     *
     * @param root 存储根
     * @param key  条目 key
     * @return 绝对路径；越界返回 null
     */
    private Path resolveLocal(Path root, String key) {
        String normalized = HostFilePaths.normalizeKey(key);
        if (normalized == null || normalized.isEmpty()) {
            return root;
        }
        Path target = root.resolve(normalized).normalize();
        return target.startsWith(root) ? target : null;
    }

    /**
     * 取本地存储根目录。
     *
     * @param storage 存储
     * @return 根目录；非本地存储返回 null
     */
    private Path localRoot(FileStorage storage) {
        if (storage instanceof LocalFileStorage local) {
            return local.getRootPath();
        }
        return null;
    }

    /**
     * 当前请求对应的存储。
     *
     * @param bucket 存储提供者名
     * @return 存储；未启用或提供者不存在返回 null
     */
    private FileStorage requireStorage(String bucket) {
        FileStorageTemplate template = template();
        if (template == null) {
            return null;
        }
        String name = resolveName(template, bucket);
        return name.isEmpty() ? null : storageOf(template, name);
    }

    /**
     * 解析提供者名：显式传入优先，否则默认提供者。
     *
     * @param template 存储模板
     * @param bucket   提供者名
     * @return 提供者名；无可用提供者返回空串
     */
    private String resolveName(FileStorageTemplate template, String bucket) {
        if (bucket != null && !bucket.isBlank()) {
            return bucket.trim();
        }
        String defaultName = template.getDefaultStorageName();
        return defaultName == null ? "" : defaultName;
    }

    /**
     * 按名称取存储。
     *
     * @param template 存储模板
     * @param name     提供者名
     * @return 存储；不存在返回 null
     */
    private FileStorage storageOf(FileStorageTemplate template, String name) {
        FileStorage storage = template.getStorage(name);
        return storage != null ? storage : template.getDefaultStorage();
    }

    /**
     * 存储模板。
     *
     * @return 模板；宿主未装配返回 null
     */
    private FileStorageTemplate template() {
        return templateProvider.getIfAvailable();
    }

    /**
     * 收敛分页大小。
     *
     * @param limit 请求值
     * @return 合法页大小
     */
    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 取异常或结果里的可读消息。
     *
     * @param source 消息或异常
     * @return 非空消息
     */
    private String messageOf(Object source) {
        if (source == null) {
            return "未知错误";
        }
        if (source instanceof Throwable throwable) {
            return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
        }
        String message = String.valueOf(source);
        return message.isBlank() ? "未知错误" : message;
    }
}
