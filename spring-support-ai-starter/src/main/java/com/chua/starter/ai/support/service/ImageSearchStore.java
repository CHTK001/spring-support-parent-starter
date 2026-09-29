package com.chua.starter.ai.support.service;

import com.chua.common.support.vector.Vector;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.jvector.support.spi.JVectorVectorStorageProvider;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 以图搜图底库：图像特征向量的存取与相似度检索。
 *
 * <p><b>向量存储按配置选择</b>：读取 {@code image_search.vector_storage_engine}
 * （缺省 {@code jvector}），通过 {@link VectorStorageProvider#create} 构建，
 * 与「人脸识别」「RAG」保持同一套向量存储机制，不再自建内存索引。</p>
 *
 * <p>特征模型取自 {@code image_search.default_image_feature_model}，
 * 通过 {@link FeatureExtractor#create(String)} 构建。</p>
 *
 * <p>向量存储的维度在首次取到特征时才确定（不同模型维度不同），因此
 * {@link #ensureStorage(int)} 采用懒建；元数据（名称/缩略图/尺寸）与原始向量
 * 另外落一份 JSON 快照，启动时据此重建向量索引，使底库可跨重启保留。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageSearchStore {

    /** 配置分组名 */
    private static final String GROUP = "image_search";
    /** 默认向量存储实现（与部署默认一致） */
    private static final String DEFAULT_STORAGE = "jvector";
    /** 缩略图最长边（前端以 base64 内联展示，不宜过大） */
    private static final int THUMB_MAX_EDGE = 240;
    /** 向量标识前缀（避免与其它业务在共享存储中撞键） */
    private static final String VECTOR_ID_PREFIX = "image-search-";
    /** 归一化前的最小模长，避免除零 */
    private static final double MIN_NORM = 1e-12d;

    private final GlobalSettingReader settingReader;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, StoredImage> images = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /** 当前生效的特征模型标识（空表示未配置） */
    private volatile String modelName = "";
    /** 特征抽取器缓存；模型变更后置空重建 */
    private volatile FeatureExtractor extractor;
    /** 向量存储实例（按首次确定的维度懒建） */
    private volatile VectorStorage storage;
    /** 向量存储实际维度 */
    private volatile int storageDimension;
    /** 向量存储实现名（用于状态展示） */
    private volatile String storageEngine = "";
    /**
     * 当前向量索引对应的特征模型标识。
     *
     * <p>参与 {@link #ensureStorage(int)} 的复用判定：换了特征模型意味着整个特征空间
     * 变了，旧索引里的向量与新查询向量不可比，必须重建。只比维度是不够的——
     * 不同模型完全可能输出同样的维度，那样会静默沿用旧索引，检索结果看似正常、实则无意义。</p>
     */
    private volatile String storageModel = "";

    private Path storeDir() {
        return Paths.get(System.getProperty("image.search.store.dir", "./image-search-store"));
    }

    /**
     * 原图目录路径。
     *
     * <p>原图**不进 index.json** —— 快照里只放元数据与缩略图（base64 最长边
     * {@value #THUMB_MAX_EDGE}px），原图单独落盘，否则一份底库快照会被原始像素
     * 撑到几十上百 MB，每次 {@link #persist()} 都要全量重写。</p>
     *
     * @return 原图目录（{@code <storeDir>/images}）
     */
    private Path imageDir() {
        return storeDir().resolve("images");
    }

    /**
     * 底库快照文件路径。
     *
     * <p>默认落在工作目录下的 {@code image-search-store/index.json}（远端即
     * {@code /opt/ai-starter/runtime/image-search-store/index.json}），
     * 可用系统属性 {@code image.search.store.dir} 覆盖。</p>
     *
     * @return 快照文件路径
     */
    private Path storeFile() {
        return storeDir().resolve("index.json");
    }

    /**
     * 启动时加载底库快照；向量索引在首次取到维度后重建。
     */
    @PostConstruct
    public void load() {
        Path file = storeFile();
        try {
            if (!Files.isRegularFile(file)) {
                log.info("[ImageSearch] 底库快照不存在，从空库开始: {}", file.toAbsolutePath());
                return;
            }
            byte[] raw = Files.readAllBytes(file);
            List<StoredImage> loaded = objectMapper.readValue(raw, new TypeReference<List<StoredImage>>() {
            });
            synchronized (images) {
                images.clear();
                long maxId = 0L;
                for (StoredImage item : loaded) {
                    if (item == null) {
                        continue;
                    }
                    backfillSize(item);
                    images.put(item.getId(), item);
                    maxId = Math.max(maxId, item.getId());
                }
                sequence.set(maxId);
            }
            log.info("[ImageSearch] 底库快照加载完成: {} 条 ({})", images.size(), file.toAbsolutePath());
        } catch (Exception ex) {
            log.warn("[ImageSearch] 底库加载失败，按空库启动: {} -> {}", file.toAbsolutePath(), ex.getMessage());
        }
    }

    /**
     * 补齐历史记录缺失的体积。
     *
     * <p>统计卡要显示「占用体积」，而 {@code sizeBytes} 是后加的字段，
     * 早期入库的记录没有它 —— 不补的话整块统计长期显示「未知」，
     * 用户会以为统计坏了。口径取「该记录真实占用」：</p>
     * <ul>
     *   <li>有原图文件 → 取文件实际字节数（可能是更早版本写入的，以磁盘为准）</li>
     *   <li>无原图（只有内嵌缩略图）→ 取缩略图 base64 解码后的字节数</li>
     * </ul>
     *
     * <p>每次启动补一次，随后会随快照一并落盘，不是每请求都做 IO。</p>
     *
     * @param item 待补齐的记录
     */
    private void backfillSize(StoredImage item) {
        if (item.getSizeBytes() != null && item.getSizeBytes() > 0) {
            return;
        }
        if (item.getImageFile() != null && !item.getImageFile().isBlank()) {
            try {
                Path original = imageDir().resolve(item.getImageFile());
                if (Files.isRegularFile(original)) {
                    item.setSizeBytes(Files.size(original));
                    return;
                }
            } catch (Exception ex) {
                log.debug("[ImageSearch] 原图体积读取失败: id={} -> {}", item.getId(), ex.getMessage());
            }
        }
        // 退回缩略图体积：base64 解出来的字节数才是真实占用，不能直接取字符串长度
        String thumb = item.getThumb();
        if (thumb != null && !thumb.isBlank()) {
            item.setSizeBytes((long) (thumb.length() / 4 * 3));
        }
    }

    /**
     * 关闭向量存储，释放其持有的资源（如 jvector 的文件句柄）。
     */
    @PreDestroy
    public void shutdown() {
        VectorStorage current = storage;
        if (current != null) {
            try {
                current.rebuild();
            } catch (Exception ex) {
                log.debug("[ImageSearch] 关闭前持久化失败: {}", ex.getMessage());
            }
            try {
                current.close();
            } catch (Exception ex) {
                log.debug("[ImageSearch] 关闭向量存储失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * 当前配置的特征模型标识。
     *
     * @return 模型标识；未配置时为空串
     */
    public String currentModel() {
        try {
            String v = settingReader.getStr(GROUP, "default_image_feature_model");
            return v == null ? "" : v.trim();
        } catch (Exception ex) {
            log.warn("[ImageSearch] 读取特征模型配置失败: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * 当前配置的向量存储实现名。
     *
     * @return 存储实现名（缺省 {@code jvector}）
     */
    public String currentStorageEngine() {
        try {
            String v = settingReader.getStr(GROUP, "vector_storage_engine");
            return v == null || v.isBlank() ? DEFAULT_STORAGE : v.trim();
        } catch (Exception ex) {
            return DEFAULT_STORAGE;
        }
    }

    /**
     * 判断以图搜图是否可用。
     *
     * @return true 表示已配置特征模型
     */
    public boolean available() {
        return !currentModel().isBlank();
    }

    /**
     * 取当前特征抽取器（懒建 + 缓存）。
     *
     * <p>每次都比对当前配置与缓存所用模型，不一致即重建——因此在「默认模型」里
     * 换特征模型**无需重启**，下一次抽取就会用上新模型。</p>
     *
     * @return 特征抽取器
     */
    private FeatureExtractor extractor() {
        String want = currentModel();
        if (want.isBlank()) {
            throw new IllegalStateException(
                    "以图搜图未启用：请先在设置中配置 image_search 分组的 default_image_feature_model");
        }
        FeatureExtractor current = extractor;
        if (current != null && want.equals(modelName)) {
            return current;
        }
        synchronized (this) {
            if (extractor == null || !want.equals(modelName)) {
                try {
                    extractor = FeatureExtractor.create(want);
                    modelName = want;
                    log.info("[ImageSearch] 特征模型已就绪: {}", want);
                } catch (Exception ex) {
                    throw new IllegalStateException(
                            "以图搜图未启用：特征模型不可用 feature_model=" + want + " -> " + ex.getMessage(), ex);
                }
            }
            return extractor;
        }
    }

    /**
     * 抽取图像特征向量（已 L2 归一化）。
     *
     * @param imageData 图片字节
     * @return 归一化后的特征向量
     */
    public float[] extract(byte[] imageData) {
        float[] raw = extractor().extract(imageData);
        if (raw == null || raw.length == 0) {
            throw new IllegalStateException("以图搜图特征抽取为空：feature_model=" + currentModel());
        }
        return l2Normalize(raw);
    }

    /**
     * 确保向量存储已按指定维度构建，并把已有向量灌入。
     *
     * <p>维度由模型输出决定，因此只能在首次取到特征后构建。若与配置的
     * {@code feature_dimension} 不一致，以实际维度为准并告警——写死配置维度会让
     * 向量写入失败或静默截断。</p>
     *
     * @param dimension 实际特征维度
     * @return 向量存储
     */
    private VectorStorage ensureStorage(int dimension) {
        // 复用条件必须同时覆盖 维度 / 存储实现 / 特征模型 三者。
        // 早期实现只比维度，于是「在默认模型里改了 vector_storage_engine」不会生效：
        // 维度没变就直接复用旧实例，用户改完配置却毫无反应，只能靠重启碰运气。
        VectorStorage current = storage;
        if (current != null && current.dimension() == dimension
                && currentStorageEngine().equals(storageEngine)
                && currentModel().equals(storageModel)) {
            return current;
        }
        synchronized (this) {
            String engine = currentStorageEngine();
            String model = currentModel();
            if (storage != null && storage.dimension() == dimension
                    && engine.equals(storageEngine) && model.equals(storageModel)) {
                return storage;
            }
            if (storage != null) {
                log.info("[ImageSearch] 向量存储配置已变更（engine: {} -> {}, model: {} -> {}），重建索引",
                        storageEngine, engine, storageModel, model);
                try {
                    storage.close();
                } catch (Exception ex) {
                    log.debug("[ImageSearch] 重建前关闭旧存储失败: {}", ex.getMessage());
                }
                storage = null;
            }
            int configured = readInt("feature_dimension", 0);
            if (configured > 0 && configured != dimension) {
                log.warn("[ImageSearch] 配置 feature_dimension={} 与实际模型输出 {} 不一致，以实际维度为准",
                        configured, dimension);
            }
            try {
                // jvector 必须显式给出索引落盘路径：只传默认值时会落到相对目录，
                // 写入直接返回 false（表现为「向量写入失败」）。与 RAG 的做法一致，
                // 这里为本底库指定独立的索引目录，避免与 RAG 的索引互相干扰。
                Map<String, Object> config = new LinkedHashMap<>(2);
                config.put(JVectorVectorStorageProvider.KEY_INDEX_PATH,
                        storeDir().resolve("jvector-index").toAbsolutePath().toString());
                VectorStorage created = VectorStorageProvider.createDynamic(
                        engine, dimension, VectorCompareAlgorithm.cosine(), config);
                // 必须在灌入前清空。jvector 的 LARGER_THAN_MEMORY 策略会在构造时把磁盘
                // 侧录文件（<indexPath>.vectors）里的 id 全部读回内存序数表；而
                // remove() 只在向量存储已初始化时才通知它删除，删除发生在懒加载之前
                // 时这条通知会丢失，于是已删记录的 id 永久残留在侧录里。直接往这种
                // 存储里灌快照会与残留 id 撞键，add 返回 false，而旧的代码无视返回值
                // 仍然累加计数，日志谎报「已灌入 N 条」，把故障伪装成正常。
                // index.json 才是唯一权威源，向量索引完全可由它重建，因此先清空。
                created.clear();
                int restored = 0;
                int skipped = 0;
                int stale = 0;
                synchronized (images) {
                    for (StoredImage item : images.values()) {
                        float[] v = item.getVector();
                        if (v == null || v.length != dimension) {
                            skipped++;
                        } else if (item.getFeatureModel() != null
                                && !model.equals(item.getFeatureModel())) {
                            // 换模型后旧向量属于另一个特征空间，与新查询向量不可比。
                            // 灌进去会让检索「有结果但排序随机」，比查不到更难排查，
                            // 因此宁可跳过，由管理员按新模型重新入库。
                            skipped++;
                            stale++;
                        } else if (created.add(vectorId(item.getId()), v)) {
                            restored++;
                        } else {
                            // 清空之后仍写不进，只能说明该 id 在本进程内重复出现，
                            // 属于快照自相矛盾，必须让日志看得出来而不是静默吞掉。
                            skipped++;
                        }
                    }
                }
                storage = created;
                storageDimension = dimension;
                storageEngine = engine;
                storageModel = model;
                log.info("[ImageSearch] 向量存储已就绪: engine={}, model={}, dimension={}, 已灌入={} 条, 跳过={} 条",
                        engine, model, dimension, restored, skipped);
                if (stale > 0) {
                    log.warn("[ImageSearch] 特征模型已由旧模型切换为 {}，{} 条历史记录的向量属于旧特征空间、"
                                    + "已跳过重建，这些图在重新入库前检索不到（记录与缩略图仍在列表中，可正常查看和删除）",
                            model, stale);
                }
                return created;
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "以图搜图向量存储不可用：engine=" + engine + ", dimension=" + dimension + " -> " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * 加入底库。
     *
     * @param name      展示名
     * @param imageData 原始图片字节
     * @param vector    已归一化特征向量
     * @return 入库记录
     */
    public StoredImage add(String name, byte[] imageData, float[] vector) {
        VectorStorage store = ensureStorage(vector.length);
        long id = sequence.incrementAndGet();
        StoredImage item = new StoredImage();
        item.setId(id);
        item.setName(name == null || name.isBlank() ? ("未命名-" + id) : name.trim());
        item.setTags(List.of());
        item.setVector(vector);
        item.setFeatureModel(currentModel());
        item.setCreatedAt(System.currentTimeMillis());
        // 记原图真实字节数：列表要展示体积，而尺寸无法由缩略图反推
        item.setSizeBytes((long) imageData.length);
        item.setThumb(thumbnailBase64(imageData));
        int[] size = imageSize(imageData);
        item.setWidth(size[0]);
        item.setHeight(size[1]);
        // 原图单独落盘，供「点击查看」用；写失败不阻断入库 ——
        // 底库的核心价值是向量，缺一张原图只影响查看，不该让整次上传失败。
        String fileName = writeOriginal(id, imageData);
        item.setImageFile(fileName);
        if (!store.add(vectorId(id), vector)) {
            // 原图已在上一步落盘，这里必须回滚，否则每次失败的入库都会在 images/
            // 下留一个没有任何记录指向的孤儿文件（实测 id=8 即为如此）。
            deleteOriginal(item);
            throw new IllegalStateException("以图搜图向量写入失败：engine=" + storageEngine + ", id=" + id);
        }
        synchronized (images) {
            images.put(id, item);
        }
        persist();
        return item;
    }

    /**
     * 读取底库记录的原图字节。
     *
     * <p>历史快照（本次改动之前入库的记录）没有原图文件，返回 {@code null}，
     * 由调用方回退到缩略图。</p>
     *
     * @param id 记录标识
     * @return 原图字节；无原图或文件已丢失时返回 {@code null}
     */
    public byte[] readOriginal(long id) {
        StoredImage item;
        synchronized (images) {
            item = images.get(id);
        }
        if (item == null || item.getImageFile() == null || item.getImageFile().isBlank()) {
            return null;
        }
        try {
            Path file = imageDir().resolve(item.getImageFile());
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return Files.readAllBytes(file);
        } catch (Exception ex) {
            log.warn("[ImageSearch] 读取原图失败: id={} -> {}", id, ex.getMessage());
            return null;
        }
    }

    /**
     * 读取底库记录的缩略图字节（无原图时的查看回退）。
     *
     * @param id 记录标识
     * @return JPEG 字节；记录不存在或无缩略图时返回 {@code null}
     */
    public byte[] readThumbnail(long id) {
        StoredImage item;
        synchronized (images) {
            item = images.get(id);
        }
        if (item == null || item.getThumb() == null || item.getThumb().isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(item.getThumb());
        } catch (Exception ex) {
            log.warn("[ImageSearch] 缩略图解码失败: id={} -> {}", id, ex.getMessage());
            return null;
        }
    }

    /**
     * 把原图写入 {@code <storeDir>/images/}。
     *
     * @param id        记录标识
     * @param imageData 原始图片字节
     * @return 落盘文件名；失败返回 {@code null}
     */
    private String writeOriginal(long id, byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return null;
        }
        String ext = detectImageExtension(imageData);
        String fileName = id + "." + ext;
        try {
            Path dir = imageDir();
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            Path tmp = target.resolveSibling(fileName + ".tmp");
            Files.write(tmp, imageData);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (Exception ex) {
            log.warn("[ImageSearch] 原图落盘失败（不影响入库）: id={} -> {}", id, ex.getMessage());
            return null;
        }
    }

    /**
     * 删除一条记录的原图文件（尽力而为，失败只告警）。
     *
     * @param item 待删记录；为 {@code null} 或无原图文件名时直接返回
     */
    private void deleteOriginal(StoredImage item) {
        if (item == null || item.getImageFile() == null || item.getImageFile().isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(imageDir().resolve(item.getImageFile()));
        } catch (Exception ex) {
            log.warn("[ImageSearch] 原图文件删除失败: id={} -> {}", item.getId(), ex.getMessage());
        }
    }

    /**
     * 按魔数识别图片格式。
     *
     * <p>不信任上传时的 Content-Type 或文件名后缀，只看字节头。</p>
     *
     * @param data 图片字节
     * @return 扩展名（不含点）；无法识别时返回 {@code bin}
     */
    private static String detectImageExtension(byte[] data) {
        if (data == null || data.length < 4) {
            return "bin";
        }
        int b0 = data[0] & 0xFF;
        int b1 = data[1] & 0xFF;
        int b2 = data[2] & 0xFF;
        int b3 = data[3] & 0xFF;
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
            return "png";
        }
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return "jpg";
        }
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) {
            return "gif";
        }
        if (b0 == 0x42 && b1 == 0x4D) {
            return "bmp";
        }
        if (data.length >= 12 && b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46
                && (data[8] & 0xFF) == 0x57 && (data[9] & 0xFF) == 0x45) {
            return "webp";
        }
        return "bin";
    }

    /**
     * 按向量检索底库（余弦相似度）。
     *
     * <p>{@link Vector} 不携带分数，这里对命中项用与存储一致的余弦算法重新计算，
     * 保证「返回顺序」与「展示分数」同源。</p>
     *
     * @param query  查询向量（须已归一化）
     * @param topK   返回条数上限
     * @param minSim 相似度下限
     * @return 命中的结果，按相似度降序
     */
    public List<Match> search(float[] query, int topK, double minSim) {
        VectorStorage store = ensureStorage(query.length);
        List<Vector> hits = store.search(query, Math.max(1, topK));
        List<Match> out = new ArrayList<>();
        if (hits == null) {
            return out;
        }
        VectorCompareAlgorithm cosine = VectorCompareAlgorithm.cosine();
        for (Vector hit : hits) {
            Long id = parseId(hit.id());
            if (id == null) {
                continue;
            }
            StoredImage item;
            synchronized (images) {
                item = images.get(id);
            }
            if (item == null || item.getVector() == null) {
                continue;
            }
            double score = cosine.compare(query, item.getVector());
            if (score >= minSim) {
                out.add(new Match(item, score));
            }
        }
        out.sort(Comparator.comparingDouble(Match::score).reversed());
        return out;
    }

    /**
     * 分页列出底库。
     *
     * @param page    页码，从 1 开始
     * @param size    每页条数
     * @param keyword 名称关键词，可为空
     * @return 当前页记录与总数
     */
    public Map<String, Object> list(int page, int size, String keyword) {
        int p = Math.max(1, page);
        int s = Math.max(1, Math.min(200, size));
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<StoredImage> snapshot;
        synchronized (images) {
            snapshot = new ArrayList<>(images.values());
        }
        snapshot.sort(Comparator.comparingLong(StoredImage::getId).reversed());
        List<StoredImage> filtered = new ArrayList<>();
        for (StoredImage item : snapshot) {
            if (!kw.isEmpty() && !item.getName().toLowerCase(Locale.ROOT).contains(kw)) {
                continue;
            }
            filtered.add(item);
        }
        int from = Math.min((p - 1) * s, filtered.size());
        int to = Math.min(from + s, filtered.size());
        Map<String, Object> out = new LinkedHashMap<>(2);
        out.put("items", new ArrayList<>(filtered.subList(from, to)));
        out.put("total", filtered.size());
        return out;
    }

    /**
     * 删除一条底库记录。
     *
     * @param id 记录标识
     * @return true 表示确实删掉了一条
     */
    public boolean remove(long id) {
        boolean removed;
        StoredImage removedItem;
        synchronized (images) {
            removedItem = images.remove(id);
            removed = removedItem != null;
        }
        if (!removed) {
            return false;
        }
        // 原图一并删掉，否则 images/ 会随删除次数无限堆积。
        // 必须在上面把记录移出 images 之前拿到引用 —— 移出后就查不到文件名了。
        deleteOriginal(removedItem);
        VectorStorage store = storage;
        if (store != null) {
            try {
                store.remove(vectorId(id));
            } catch (Exception ex) {
                log.warn("[ImageSearch] 向量存储删除失败（快照仍会移除）: id={} -> {}", id, ex.getMessage());
            }
        }
        persist();
        return true;
    }

    /**
     * 底库记录数。
     *
     * @return 记录数
     */
    public int size() {
        synchronized (images) {
            return images.size();
        }
    }

    /**
     * 向量维度（取底库任一条；空库返回 0）。
     *
     * @return 向量维度
     */
    public int dimension() {
        int d = storageDimension;
        if (d > 0) {
            return d;
        }
        synchronized (images) {
            for (StoredImage item : images.values()) {
                float[] v = item.getVector();
                if (v != null) {
                    return v.length;
                }
            }
        }
        return 0;
    }

    /**
     * 读取整数配置，未配置或非法时返回默认值。
     *
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 配置值
     */
    private int readInt(String field, int defaultValue) {
        try {
            String v = settingReader.getStr(GROUP, field);
            if (v == null || v.isBlank()) {
                return defaultValue;
            }
            return Integer.parseInt(v.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    /**
     * 向量标识（带业务前缀，避免共享存储中撞键）。
     *
     * @param id 记录标识
     * @return 向量标识
     */
    private static String vectorId(long id) {
        return VECTOR_ID_PREFIX + id;
    }

    /**
     * 从向量标识解析记录标识。
     *
     * @param vectorId 向量标识
     * @return 记录标识；非本业务前缀时返回 空
     */
    private static Long parseId(String vectorId) {
        if (vectorId == null || !vectorId.startsWith(VECTOR_ID_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(vectorId.substring(VECTOR_ID_PREFIX.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 生成缩略图的 JPEG base64（不生成失败时返回 null，不影响入库）。
     *
     * @param imageData 原始图片字节
     * @return base64 字符串；失败时为 null
     */
    private String thumbnailBase64(byte[] imageData) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageData));
            if (src == null) {
                return null;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            double scale = Math.min(1d, (double) THUMB_MAX_EDGE / Math.max(w, h));
            int tw = Math.max(1, (int) Math.round(w * scale));
            int th = Math.max(1, (int) Math.round(h * scale));
            BufferedImage dst = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, tw, th, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(dst, "jpg", bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception ex) {
            log.debug("[ImageSearch] 生成缩略图失败（忽略）: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 读取图片宽高（读不出时返回 0,0）。
     *
     * @param imageData 原始图片字节
     * @return 长度 2 的数组：[宽, 高]
     */
    private int[] imageSize(byte[] imageData) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageData));
            if (src != null) {
                return new int[]{src.getWidth(), src.getHeight()};
            }
        } catch (Exception ex) {
            log.debug("[ImageSearch] 读取图片尺寸失败（忽略）: {}", ex.getMessage());
        }
        return new int[]{0, 0};
    }

    /**
     * 原地做 L2 归一化并返回同一数组。
     *
     * @param vector 原始向量
     * @return 归一化后的向量（入参本身）
     */
    private static float[] l2Normalize(float[] vector) {
        double sum = 0d;
        for (float v : vector) {
            sum += (double) v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm < MIN_NORM) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    /**
     * 把元数据与向量写盘：先写临时文件，再原子替换快照。
     *
     * <p>该快照是向量索引的重建来源（启动时按它灌入向量存储），因此必须包含原始向量。</p>
     */
    private void persist() {
        Path file = storeFile();
        try {
            Files.createDirectories(file.getParent());
            List<StoredImage> snapshot;
            synchronized (images) {
                snapshot = new ArrayList<>(images.values());
            }
            byte[] raw = objectMapper.writeValueAsBytes(snapshot);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, raw);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            log.warn("[ImageSearch] 底库写盘失败（内存库仍可用）: {} -> {}", file, ex.getMessage());
        }
    }

    /**
     * 底库条目（持久化实体，字段可变以便 Jackson 读写）。
     */
    public static class StoredImage {

        /** 记录标识 */
        private long id;
        /** 展示名 */
        private String name;
        /** 标签 */
        private List<String> tags = new ArrayList<>();
        /** 缩略图 JPEG base64 */
        private String thumb;
        /**
         * 原图文件名（相对 {@code <storeDir>/images/}），如 {@code 12.png}。
         *
         * <p>本次改动之前入库的历史记录没有该字段，反序列化后为 {@code null}，
         * 此时「查看原图」由调用方回退到缩略图。</p>
         */
        private String imageFile;
        /** 原图宽 */
        private int width;
        /** 原图高 */
        private int height;
        /** 入库时间（毫秒） */
        private long createdAt;
        /** 归一化特征向量 */
        private float[] vector;
        /**
         * 产出该向量的特征模型标识。
         *
         * <p>不同模型的向量落在不同的特征空间里，跨模型混用会让相似度彻底失去意义
         * （检索看起来「有结果」，但排序完全随机）。记录来源模型后，切换模型即可判定
         * 哪些向量已失效并跳过重建，而不是把它们当成有效数据灌进新索引。</p>
         *
         * <p>本字段之前入库的历史记录为 {@code null}，按「与当前模型一致」处理，
         * 以免一次模型切换就把存量底库整体作废；这些记录在重新入库后才会带上标识。</p>
         */
        private String featureModel;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String getThumb() {
            return thumb;
        }

        public void setThumb(String thumb) {
            this.thumb = thumb;
        }

        public String getImageFile() {
            return imageFile;
        }

        public void setImageFile(String imageFile) {
            this.imageFile = imageFile;
        }

        /**
         * 原图字节数。
         *
         * <p>列表页要显示「640×480 · 186 KB」这类信息，而底库原先只存了维度与缩略图，
         * 尺寸得由前端拿 base64 去反推（不准）。入库时记一份真实字节数最省事。</p>
         *
         * <p>本字段之前入库的历史记录为 {@code null}，前端按「未知」展示，
         * 不用缩略图长度冒充原图大小 —— 那会把 30KB 的缩略图显示成原图体积。</p>
         */
        private Long sizeBytes;

        public Long getSizeBytes() {
            return sizeBytes;
        }

        public void setSizeBytes(Long sizeBytes) {
            this.sizeBytes = sizeBytes;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }

        public float[] getVector() {
            return vector;
        }

        public void setVector(float[] vector) {
            this.vector = vector;
        }

        public String getFeatureModel() {
            return featureModel;
        }

        public void setFeatureModel(String featureModel) {
            this.featureModel = featureModel;
        }
    }

    /**
     * 检索命中项。
     *
     * @param image 命中的底库记录
     * @param score 余弦相似度
     */
    public record Match(StoredImage image, double score) {
    }
}
