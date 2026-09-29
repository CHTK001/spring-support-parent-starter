package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.service.ImageSearchStore;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 以图搜图控制器。
 *
 * <p>提供「提特征入库」与「按图检索底库」能力，特征模型来自
 * {@code image_search} 分组全局设置，底库由 {@link ImageSearchStore} 负责。</p>
 *
 * <p>未配置特征模型时，错误信息中会包含 {@code feature_model} 或「未启用」，
 * 前端据此把入口置灰。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/image-search")
@RequiredArgsConstructor
@Tag(name = "AI 以图搜图", description = "图像特征入库、按图检索、底库管理")
public class ImageSearchController {

    /** 配置分组名 */
    private static final String GROUP = "image_search";
    /**
     * 相似度下限的兜底值。
     *
     * <p>与 {@code ImageSearchGlobalSettingProvider} 登记的默认值 0.76 保持一致。
     * 早期这里是 0.3，与页面上的 0.76 差了两倍多：一旦配置行缺失或被写成越界值，
     * 检索会静默退化成「几乎什么都返回」，且没有任何报错——比直接失败更难察觉。
     * 兜底值必须与登记默认值同源，否则等于埋了一个只在异常路径生效的隐藏开关。</p>
     */
    private static final double DEFAULT_MIN_SIMILARITY = 0.76d;
    /** 返回条数上限的兜底值，与 provider 登记的 search_top_k 默认值一致 */
    private static final int DEFAULT_TOP_K = 30;
    /** 返回条数的合法区间 */
    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 200;

    private final ImageSearchStore imageSearchStore;
    private final GlobalSettingReader settingReader;

    /**
     * 以图搜图可用状态。
     *
     * @return {@code available} 表示是否已配置特征模型，{@code model} 为当前模型标识
     */
    @Permission("admin")
    @Operation(summary = "以图搜图状态", description = "返回是否已配置特征模型及当前模型标识")
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> status() {
        Map<String, Object> out = new LinkedHashMap<>(6);
        out.put("available", imageSearchStore.available());
        out.put("model", imageSearchStore.currentModel());
        out.put("storage", imageSearchStore.currentStorageEngine());
        out.put("count", imageSearchStore.size());
        out.put("dimension", imageSearchStore.dimension());
        return ReturnResult.ok(out);
    }

    /**
     * 把一张图片加入底库。
     *
     * @param file 图片文件
     * @param name 展示名，缺省取文件名
     * @return 入库记录标识与名称
     */
    @Permission("admin")
    @Operation(summary = "加入图片库", description = "上传图片并提取特征后写入底库")
    @PostMapping("/index")
    public ReturnResult<Map<String, Object>> index(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "name", required = false) String name) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            byte[] data = file.getBytes();
            float[] vector = imageSearchStore.extract(data);
            String shown = name;
            if (shown == null || shown.isBlank()) {
                shown = file.getOriginalFilename();
            }
            ImageSearchStore.StoredImage saved = imageSearchStore.add(shown, data, vector);
            Map<String, Object> out = new LinkedHashMap<>(4);
            out.put("id", saved.getId());
            out.put("name", saved.getName());
            out.put("dimension", vector.length);
            return ReturnResult.ok(out);
        } catch (Exception ex) {
            log.error("[ImageSearch] 入库失败", ex);
            return ReturnResult.error("入库失败: " + ex.getMessage());
        }
    }

    /**
     * 按图检索底库。
     *
     * @param file 查询图片
     * @param topK 返回条数上限；不传则取 {@code image_search.search_top_k} 配置
     * @return 命中列表，按相似度降序；每项含 id/name/tags/thumbnail/score
     */
    @Permission("admin")
    @Operation(summary = "以图搜图", description = "上传查询图，返回底库中相似度最高的若干张")
    @PostMapping("/search")
    public ReturnResult<List<Map<String, Object>>> search(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "topK", required = false) Integer topK) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            int k = resolveTopK(topK);
            float[] query = imageSearchStore.extract(file.getBytes());
            List<ImageSearchStore.Match> matches = imageSearchStore.search(query, k, minSimilarity());
            List<Map<String, Object>> out = new ArrayList<>(matches.size());
            for (ImageSearchStore.Match m : matches) {
                out.add(toItem(m.image(), m.score()));
            }
            return ReturnResult.ok(out);
        } catch (Exception ex) {
            log.error("[ImageSearch] 检索失败", ex);
            return ReturnResult.error("检索失败: " + ex.getMessage());
        }
    }

    /**
     * 分页查看底库。
     *
     * @param page    页码，从 1 开始
     * @param size    每页条数
     * @param keyword 名称关键词，可为空
     * @return 含 records 风格字段的 {@code items} 与 {@code total}
     */
    @Permission("admin")
    @Operation(summary = "图片库列表", description = "分页查看底库图片（不含原始特征值）")
    @GetMapping("/list")
    public ReturnResult<Map<String, Object>> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                                  @RequestParam(value = "size", defaultValue = "24") int size,
                                                  @RequestParam(value = "keyword", required = false) String keyword) {
        try {
            Map<String, Object> raw = imageSearchStore.list(page, size, keyword);
            Object items = raw.get("items");
            List<Map<String, Object>> out = new ArrayList<>();
            if (items instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof ImageSearchStore.StoredImage img) {
                        out.add(toItem(img, null));
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>(4);
            result.put("items", out);
            result.put("total", raw.get("total"));
            result.put("page", Math.max(1, page));
            result.put("size", size);
            return ReturnResult.ok(result);
        } catch (Exception ex) {
            log.error("[ImageSearch] 列表查询失败", ex);
            return ReturnResult.error("列表查询失败: " + ex.getMessage());
        }
    }

    /**
     * 从底库删除一条记录。
     *
     * @param id 记录标识
     * @return true 表示删除成功
     */
    @Permission("admin")
    @Operation(summary = "删除图片", description = "按标识从底库删除一条记录")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable("id") long id) {
        try {
            boolean removed = imageSearchStore.remove(id);
            if (!removed) {
                return ReturnResult.error("记录不存在: " + id);
            }
            return ReturnResult.ok(true);
        } catch (Exception ex) {
            log.error("[ImageSearch] 删除失败", ex);
            return ReturnResult.error("删除失败: " + ex.getMessage());
        }
    }

    /**
     * 读取底库记录的原图，用于「点击缩略图查看大图」。
     *
     * <p>本次改动之前入库的历史记录没有落盘原图（快照里只存了最长边 240px 的
     * 缩略图），此时自动回退返回缩略图，保证前端不出现裂图。</p>
     *
     * @param id 记录标识
     * @return 原图字节；记录不存在时返回 404
     */
    @Permission("admin")
    @Operation(summary = "图片原图", description = "按标识返回底库图片的原图字节（无原图时回退缩略图）")
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable("id") long id) {
        byte[] original = imageSearchStore.readOriginal(id);
        if (original != null && original.length > 0) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    // 图片字节可被任意变换后缓存，这里给短 max-age，避免改库后仍看到旧图
                    .cacheControl(CacheControl.maxAge(Duration.ofSeconds(300)).cachePrivate())
                    .body(original);
        }
        byte[] thumb = imageSearchStore.readThumbnail(id);
        if (thumb == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(300)).cachePrivate())
                .body(thumb);
    }

    /**
     * 读取配置的相似度下限（非法或缺省时回退默认值）。
     *
     * @return 取值在 0~1 之间的相似度下限
     */
    private double minSimilarity() {
        try {
            BigDecimal v = settingReader.getDecimalOrNull(GROUP, "min_similarity");
            if (v == null) {
                return DEFAULT_MIN_SIMILARITY;
            }
            double d = v.doubleValue();
            if (d < 0d || d > 1d) {
                return DEFAULT_MIN_SIMILARITY;
            }
            return d;
        } catch (Exception ex) {
            return DEFAULT_MIN_SIMILARITY;
        }
    }

    /**
     * 解析单次检索返回条数上限。
     *
     * <p>取值优先级：请求显式传入的 {@code topK} &gt; {@code image_search.search_top_k}
     * 配置 &gt; 兜底常量。三者最终都会被钳制到 {@code [1, 200]}。
     * 保留请求参数是为了让调用方（如批量比对脚本）能临时放大条数，
     * 它是显式覆盖而非默认值，因此不构成「写死」。</p>
     *
     * @param requested 请求传入值，可为 {@code null}
     * @return 合法区间内的条数上限
     */
    private int resolveTopK(Integer requested) {
        int value = requested != null ? requested : configuredTopK();
        return Math.max(MIN_TOP_K, Math.min(MAX_TOP_K, value));
    }

    /**
     * 读取配置的返回条数上限（非法或缺省时回退兜底值）。
     *
     * @return 条数上限
     */
    private int configuredTopK() {
        try {
            BigDecimal v = settingReader.getDecimalOrNull(GROUP, "search_top_k");
            if (v == null) {
                return DEFAULT_TOP_K;
            }
            return v.intValue();
        } catch (Exception ex) {
            log.warn("[ImageSearch] 读取 search_top_k 失败，回退 {}: {}", DEFAULT_TOP_K, ex.getMessage());
            return DEFAULT_TOP_K;
        }
    }

    /**
     * 把底库记录转成前端可消费的条目。
     *
     * @param img   底库记录
     * @param score 相似度，列表场景传 null
     * @return 前端条目（含 id/vectorId/name/tags/thumbnail/url/hasOriginal/width/height/score）
     */
    private static Map<String, Object> toItem(ImageSearchStore.StoredImage img, Double score) {
        Map<String, Object> item = new LinkedHashMap<>(10);
        item.put("id", img.getId());
        item.put("vectorId", "img-" + img.getId());
        item.put("name", img.getName());
        item.put("tags", img.getTags() == null ? List.of() : img.getTags());
        item.put("thumbnail", img.getThumb());
        item.put("url", "/image-search/" + img.getId() + "/image");
        // 上面这个是**相对 base 的路径**，不是完整 URL，所以刻意不带 /v2。
        // 客户端会把它拼在自己的 baseURL 之后，而 baseURL 本身已经含 /v2
        // （Web 模式 /v2、Electron 模式 http://host:8787/v2）。
        // 曾经写成 "/v2/image-search/{id}/image"，前端再拼一次 baseURL，
        // 实际请求变成 /v2/v2/image-search/{id}/image → 404 → 整页缩略图全裂。
        // 参照 services/modules/workspace.api.ts：baseUrl + '/sessions/...'。
        // 历史记录没落盘原图时为 false，该接口会自动回退返回缩略图，
        // 所以 url 恒给一个可用地址，前端不必为老数据写分支。
        item.put("hasOriginal", img.getImageFile() != null && !img.getImageFile().isBlank());
        item.put("width", img.getWidth());
        item.put("height", img.getHeight());
        // 原图字节数；历史记录为 null，前端按「未知」展示而不是拿缩略图长度冒充
        item.put("size", img.getSizeBytes());
        item.put("createdAt", img.getCreatedAt());
        item.put("score", score);
        return item;
    }
}
