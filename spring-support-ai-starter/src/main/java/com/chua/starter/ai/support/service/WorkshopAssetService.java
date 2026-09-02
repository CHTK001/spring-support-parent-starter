package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.ai.support.entity.WorkshopAsset;
import com.chua.starter.ai.support.mapper.WorkshopAssetMapper;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

/**
 * 创意工坊资产服务实现。
 *
 * <p>负责资产的 CRUD、文件上传、集合重命名及物理文件管理。</p>
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkshopAssetService {

    private final WorkshopAssetMapper assetMapper;
    private final SysAiGlobalSettingService globalSettingService;

    /** 物理文件存储根目录，从全局设置读取，未配置时回退到默认值 */
    private String resolveStorageDir() {
        try {
            return globalSettingService.getValue("workshop", "storage_dir")
                    .orElse(System.getProperty("user.dir") + "/data/workshop-files");
        } catch (Exception e) {
            return System.getProperty("user.dir") + "/data/workshop-files";
        }
    }

    /**
     * 应用启动时确保存储目录存在。
     */
    @PostConstruct
    public void init() {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(resolveStorageDir()));
        } catch (Exception e) {
            log.warn("[WorkshopAsset] 创建存储目录失败（表结构未就绪，跳过）: {}", resolveStorageDir(), e);
        }
    }

    /**
     * 从全局设置读取单次上传大小上限（MB），未配置时默认 100MB。
     */
    private long resolveMaxFileSize() {
        return globalSettingService.getValue("workshop", "max_upload_size_mb")
                .map(v -> {
                    try {
                        long mb = Long.parseLong(v.trim());
                        return mb * 1024L * 1024;
                    } catch (NumberFormatException ignored) {
                        return 100L * 1024 * 1024;
                    }
                })
                .orElse(100L * 1024 * 1024);
    }

    /**
     * 分页查询资产列表，支持 kind / collection / search / tag / sort 过滤。
     *
     * @param kind     资产类型过滤（image/video/text），null 表示全部
     * @param collection 集合名称过滤，null 表示全部
     * @param ungrouped  是否仅查未分组资产
     * @param query      标题/标签模糊搜索
     * @param tag        精确标签匹配
     * @param inLibrary  是否仅在库中
     * @param sort       排序方式（created_desc/created_asc/updated_desc/name_asc/size_desc）
     * @param page       页码（从 1 开始）
     * @param pageSize   每页数量
     * @return 分页结果（items + total）
     */
    public List<WorkshopAsset> listAssets(
            String kind, String collection, boolean ungrouped,
            String query, String tag, Boolean inLibrary,
            String sort, int page, int pageSize) {
        LambdaQueryWrapper<WorkshopAsset> w = new LambdaQueryWrapper<>();

        // in_library 过滤
        if (inLibrary != null) {
            w.eq(WorkshopAsset::getInLibrary, inLibrary);
        }

        // kind 过滤
        if (kind != null && !kind.isBlank()) {
            w.eq(WorkshopAsset::getKind, kind);
        }

        // collection / ungrouped 过滤（互斥）
        if (ungrouped) {
            w.and(x -> x.isNull(WorkshopAsset::getCollection).or().eq(WorkshopAsset::getCollection, ""));
        } else if (collection != null && !collection.isBlank()) {
            w.eq(WorkshopAsset::getCollection, collection);
        }

        // 标题模糊搜索（转义 LIKE 通配符，防止注入）+ 标签 JSON_SEARCH（走 XML mapper）
        if (query != null && !query.isBlank()) {
            String escaped = escapeLike(query);
            List<String> tagIds = assetMapper.selectIdsByTags(escaped);
            w.and(x -> {
                x.like(WorkshopAsset::getTitle, escaped);
                if (!tagIds.isEmpty()) {
                    x.or().in(WorkshopAsset::getAssetId, tagIds);
                }
            });
        }

        // 排序
        switch (sort != null ? sort : "created_desc") {
            case "created_asc" -> w.orderByAsc(WorkshopAsset::getCreatedAt);
            case "updated_desc" -> w.orderByDesc(WorkshopAsset::getUpdatedAt);
            case "updated_asc" -> w.orderByAsc(WorkshopAsset::getUpdatedAt);
            case "name_asc" -> w.orderByAsc(WorkshopAsset::getTitle);
            case "size_desc" -> w.orderByDesc(WorkshopAsset::getBytes);
            case "size_asc" -> w.orderByAsc(WorkshopAsset::getBytes);
            default -> w.orderByDesc(WorkshopAsset::getCreatedAt);
        }

        Page<WorkshopAsset> p = new Page<>(page, pageSize);
        Page<WorkshopAsset> result = assetMapper.selectPage(p, w);
        return result.getRecords();
    }

    /**
     * 统计符合条件的资产总数。
     */
    public long countAssets(String kind, String collection, boolean ungrouped,
                            String query, String tag, Boolean inLibrary) {
        LambdaQueryWrapper<WorkshopAsset> w = new LambdaQueryWrapper<>();
        if (inLibrary != null) w.eq(WorkshopAsset::getInLibrary, inLibrary);
        if (kind != null && !kind.isBlank()) w.eq(WorkshopAsset::getKind, kind);
        if (ungrouped) {
            w.and(x -> x.isNull(WorkshopAsset::getCollection).or().eq(WorkshopAsset::getCollection, ""));
        } else if (collection != null && !collection.isBlank()) {
            w.eq(WorkshopAsset::getCollection, collection);
        }
        if (query != null && !query.isBlank()) {
            String escaped = escapeLike(query);
            List<String> tagIds = assetMapper.selectIdsByTags(escaped);
            w.and(x -> {
                x.like(WorkshopAsset::getTitle, escaped);
                if (!tagIds.isEmpty()) {
                    x.or().in(WorkshopAsset::getAssetId, tagIds);
                }
            });
        }
        return assetMapper.selectCount(w);
    }

    /**
     * 创建文本资产。
     */
    @Transactional
    public WorkshopAsset createTextAsset(String title, String content, String collection, List<String> tags) {
        String id = generateUuidV7();
        long now = Instant.now().toEpochMilli();
        WorkshopAsset asset = new WorkshopAsset();
        asset.setAssetId(id);
        asset.setKind("text");
        asset.setTitle(title);
        asset.setCollection(collection);
        asset.setTags(tags != null ? toJsonArray(tags) : "[]");
        asset.setInLibrary(true);
        asset.setTextContent(content);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        asset.setFilePath(""); // 文本资产无物理文件
        assetMapper.insert(asset);
        return asset;
    }

    /**
     * 上传二进制资产（图片/视频）。
     *
     * @param file       上传文件
     * @param title      标题
     * @param collection 集合
     * @param tags       标签
     * @param inLibrary  是否在库
     * @return 创建后的资产对象
     */
    @Transactional
    public WorkshopAsset uploadAsset(MultipartFile file, String title, String collection,
                                      List<String> tags, boolean inLibrary) throws IOException {
        if (file.getSize() > resolveMaxFileSize()) {
            throw new IOException("FILE_TOO_LARGE");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String ext = originalName.substring(originalName.lastIndexOf('.'));
        String fileId = generateUuidV7();
        String relativePath = "assets/" + fileId + ext;
        Path targetPath = Path.of(resolveStorageDir(), relativePath);
        Files.createDirectories(targetPath.getParent());
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        long now = Instant.now().toEpochMilli();
        WorkshopAsset asset = new WorkshopAsset();
        asset.setAssetId(fileId);
        asset.setKind(file.getContentType().startsWith("image/") ? "image" : "video");
        asset.setTitle(title != null && !title.isBlank() ? title : originalName);
        asset.setCollection(collection);
        asset.setTags(tags != null ? toJsonArray(tags) : "[]");
        asset.setMime(file.getContentType());
        asset.setBytes(file.getSize());
        asset.setInLibrary(inLibrary);
        asset.setFilePath(relativePath);
        asset.setThumbPath(null); // 缩略图由后端异步生成或前端处理
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        assetMapper.insert(asset);
        return asset;
    }

    /**
     * 更新资产元数据（标题/集合/标签/in_library）。
     */
    @Transactional
    public WorkshopAsset patchAsset(String assetId, String title, String collection,
                                      List<String> tags, Boolean inLibrary) {
        WorkshopAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new RuntimeException("资产不存在: " + assetId);
        }
        LambdaUpdateWrapper<WorkshopAsset> update = new LambdaUpdateWrapper<>();
        update.eq(WorkshopAsset::getAssetId, assetId);
        if (title != null) update.set(WorkshopAsset::getTitle, title);
        if (collection != null) update.set(WorkshopAsset::getCollection, collection.isEmpty() ? null : collection);
        if (tags != null) update.set(WorkshopAsset::getTags, toJsonArray(tags));
        if (inLibrary != null) update.set(WorkshopAsset::getInLibrary, inLibrary);
        update.set(WorkshopAsset::getUpdatedAt, Instant.now().toEpochMilli());
        assetMapper.update(null, update);
        return assetMapper.selectById(assetId);
    }

    /**
     * 删除资产（物理文件 + 数据库记录）。
     */
    @Transactional
    public void deleteAsset(String assetId) {
        WorkshopAsset asset = assetMapper.selectById(assetId);
        if (asset != null && asset.getFilePath() != null && !asset.getFilePath().isBlank()) {
            try {
                Files.deleteIfExists(Path.of(resolveStorageDir(), asset.getFilePath()));
            } catch (IOException e) {
                log.warn("[WorkshopAssetService] 删除物理文件失败: {}", asset.getFilePath(), e);
            }
        }
        assetMapper.deleteById(assetId);
    }

    /**
     * 批量重命名集合（更新所有使用该集合的资产）。
     *
     * @param from 原集合名
     * @param to   新集合名（null 表示移出分组）
     * @return 更新的记录数
     */
    @Transactional
    public int renameCollection(String from, String to) {
        LambdaUpdateWrapper<WorkshopAsset> update = new LambdaUpdateWrapper<>();
        update.eq(WorkshopAsset::getCollection, from);
        update.set(WorkshopAsset::getCollection, to == null || to.isBlank() ? null : to);
        return assetMapper.update(null, update);
    }

    /**
     * 获取资产记录。
     */
    public WorkshopAsset getAsset(String assetId) {
        return assetMapper.selectById(assetId);
    }

    /**
     * 获取所有非空集合名称列表。
     */
    public List<String> listCollections() {
        return assetMapper.selectList(
                new LambdaQueryWrapper<WorkshopAsset>()
                        .select(WorkshopAsset::getCollection)
                        .isNotNull(WorkshopAsset::getCollection)
                        .ne(WorkshopAsset::getCollection, "")
                        .groupBy(WorkshopAsset::getCollection)
        ).stream()
                .map(WorkshopAsset::getCollection)
                .distinct()
                .sorted()
                .toList();
    }

    private String toJsonArray(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(tags.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 生成 UUIDv7 格式 ID（与前端 parseAssetId 兼容：规范小写 36 字符 UUIDv7）。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateUuidV7() {
        long unixMs = System.currentTimeMillis();
        byte[] ts = new byte[6];
        for (int i = 5; i >= 0; i--) {
            ts[5 - i] = (byte) (unixMs >> (i * 8));
        }
        byte[] rand = new byte[10];
        SECURE_RANDOM.nextBytes(rand);
        byte[] uuid = new byte[16];
        System.arraycopy(ts, 0, uuid, 0, 6);
        // version 7: bits 48-51 = 0111
        uuid[6] = (byte) ((uuid[6] & 0x0F) | 0x70);
        // variant: bits 64-65 = 10
        uuid[8] = (byte) ((uuid[8] & 0x3F) | 0x80);
        return toUuidString(uuid);
    }

    private static String toUuidString(byte[] uuid) {
        return String.format("%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                uuid[0], uuid[1], uuid[2], uuid[3],
                uuid[4], uuid[5], uuid[6], uuid[7],
                uuid[8], uuid[9], uuid[10], uuid[11],
                uuid[12], uuid[13], uuid[14], uuid[15]);
    }

    /**
     * 转义 LIKE 通配符，防止查询注入（% → \%，_ → \_）。
     */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
