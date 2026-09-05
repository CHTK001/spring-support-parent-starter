package com.chua.starter.ai.support.service;

import com.chua.common.support.utils.IdUtils;
import com.chua.starter.ai.support.entity.WorkshopAsset;
import com.chua.starter.ai.support.mapper.WorkshopAssetMapper;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 把标签列表序列化为 JSON 数组字符串存储（空/空列表 -> "[]"）。
     */
    private static String toTagsJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "[]";
        try {
            return OBJECT_MAPPER.writeValueAsString(tags);
        } catch (Exception e) {
            log.warn("[WorkshopAssetService] 序列化 tags 失败", e);
            return "[]";
        }
    }

    /** 分页每页最大条数，防止超大 pageSize 拖垮查询 */
    private static final int MAX_PAGE_SIZE = 200;

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
     * 资产查询参数对象。
     *
     * @param kind       资产类型过滤（image/video/text），null 表示全部
     * @param collection 集合名称过滤，null 表示全部
     * @param ungrouped  是否仅查未分组资产
     * @param query      标题/标签模糊搜索
     * @param tag        精确标签匹配
     * @param inLibrary  是否仅在库中，null 表示不过滤
     * @param sort       排序方式（created_desc/created_asc/updated_desc/name_asc/size_desc）
     * @param page       页码（从 1 开始）
     * @param pageSize   每页数量（建议 1-200）
     */
    public record AssetQueryRequest(
            String kind,
            String collection,
            boolean ungrouped,
            String query,
            String tag,
            Boolean inLibrary,
            String sort,
            int page,
            int pageSize
    ) {
    }

    /**
     * 分页查询资产列表，支持 kind / collection / search / tag / sort 过滤。
     *
     * @param req 查询参数
     * @return 分页结果（items + total）
     */
    public List<WorkshopAsset> listAssets(AssetQueryRequest req) {
        int safePage = Math.max(req.page(), 1);
        int safePageSize = Math.min(Math.max(req.pageSize(), 1), MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safePageSize;
        return assetMapper.selectPageByFilter(
                req.kind(), req.collection(), req.ungrouped(),
                req.query(), req.tag(), req.inLibrary(),
                req.sort(), offset, safePageSize);
    }

    /**
     * 统计符合条件的资产总数。
     *
     * @param req 查询参数（仅使用过滤字段，忽略分页）
     * @return 符合过滤条件的资产总数
     */
    public long countAssets(AssetQueryRequest req) {
        return assetMapper.countByFilter(
                req.kind(), req.collection(), req.ungrouped(),
                req.query(), req.tag(), req.inLibrary());
    }

    /**
     * 创建文本资产。
     */
    @Transactional
    public WorkshopAsset createTextAsset(String title, String content, String collection, List<String> tags) {
        String id = IdUtils.uuidv7();
        long now = Instant.now().toEpochMilli();
        WorkshopAsset asset = new WorkshopAsset();
        asset.setAssetId(id);
        asset.setKind("text");
        asset.setTitle(title);
        asset.setCollection(collection);
        asset.setTags(tags != null && !tags.isEmpty() ? toTagsJson(tags) : "[]");
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
        String fileId = IdUtils.uuidv7();
        String relativePath = "assets/" + fileId + ext;
        Path targetPath = Path.of(resolveStorageDir(), relativePath);
        Files.createDirectories(targetPath.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        long now = Instant.now().toEpochMilli();
        WorkshopAsset asset = new WorkshopAsset();
        asset.setAssetId(fileId);
        asset.setKind(file.getContentType().startsWith("image/") ? "image" : "video");
        asset.setTitle(title != null && !title.isBlank() ? title : originalName);
        asset.setCollection(collection);
        asset.setTags(tags != null && !tags.isEmpty() ? toTagsJson(tags) : "[]");
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
        String tagsJson = tags != null ? (tags.isEmpty() ? "[]" : toTagsJson(tags)) : null;
        assetMapper.updateMeta(assetId, title, collection, tagsJson, inLibrary, Instant.now().toEpochMilli());
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
        if (from == null || from.isBlank()) {
            return 0;
        }
        return assetMapper.renameCollection(from, to);
    }

    /**
     * 获取资产记录。
     */
    public WorkshopAsset getAsset(String assetId) {
        return assetMapper.selectById(assetId);
    }

    /**
     * 获取所有非空集合名称列表。
     *
     * @return 去重升序的集合名列表
     */
    public List<String> listCollections() {
        return assetMapper.selectDistinctCollections();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
