package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.deeplearning.support.face.FaceAlignResult;
import com.chua.deeplearning.support.face.FaceCompareResult;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FaceIdentifyHit;
import com.chua.deeplearning.support.face.FaceLandmarkResult;
import com.chua.deeplearning.support.face.FaceSearchHit;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.FaceQualityInfo;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.service.AiFaceService;
import com.chua.starter.ai.support.service.MultiFaceEnrollException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 人脸识别控制器。
 *
 * <p>提供人脸识别单独能力、链式管线能力与辅助能力的对外接口，
 * 全部配置读取自 {@code face} 分组全局设置，配置变更实时生效。</p>
 *
 * <p><b>鉴权口径：登录即可用，不再要求管理员。</b>改造前这里 19 个接口全标
 * {@code @Permission("admin")}，理由是「人脸库是全局共享资源」。现在底库已
 * **按账号隔离**（{@code <工作目录>/<账号>/face/}，各账号一个索引文件与一份快照），
 * 每个账号只增删自己的库，该理由不再成立，因此把门槛降到「必须登录」。</p>
 *
 * <p><b>匿名访问仍被拒</b>：所有出口最终都会走到 {@code AiUserContext.requireUserId()}
 * （经底库门面解析账号），未登录时抛 401。</p>
 *
 * <p><b>档案与向量分层持久化</b>：档案字段保存在 DB（{@code sys_ai_face_library}），
 * 向量保存在 jvector（{@code VectorStorage}）；账号目录里的 {@code index.json} 只作
 * 启动重建索引的快照，不再承载列表档案。启动时按快照重建向量索引，与「以图搜图」同一套做法。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/face")
@RequiredArgsConstructor
@Tag(name = "AI 人脸识别", description = "人脸检测、特征、1:1 比对、1:N 识别、活体、动漫、超分、修复、属性、表情、关键点、质量、换脸检测")
public class FaceController {

    /**
     * 人脸识别服务
     */
    private final AiFaceService faceService;

    /**
     * 人脸检测（管线：detect → crop → liveness）。
     *
     * @param file 图片文件
     * @return 人脸检测结果
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸检测", description = "检测图片中的人脸位置、数量与活体状态")
    @PostMapping("/detect")
    public ReturnResult<?> detect(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            List<FaceDetectionHit> hits = faceService.detectPipeline(file.getBytes());
            return ReturnResult.ok(Map.of(
                    "count", hits.size(),
                    "faces", hits
            ));
        } catch (Exception e) {
            log.error("[Face] 人脸检测失败", e);
            return ReturnResult.error("人脸检测失败: " + e.getMessage());
        }
    }

    /**
     * 人脸特征提取。
     *
     * @param file 图片文件
     * @return 特征向量
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸特征提取", description = "提取图片中人脸的特征向量")
    @PostMapping("/feature")
    public ReturnResult<?> feature(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            float[] feature = faceService.extractFeature(file.getBytes());
            return ReturnResult.ok(Map.of("feature", feature));
        } catch (Exception e) {
            log.error("[Face] 特征提取失败", e);
            return ReturnResult.error("特征提取失败: " + e.getMessage());
        }
    }

    /**
     * 人脸对齐（5 点仿射旋转）。
     *
     * @param file 图片文件
     * @return 对齐结果（含对齐后的人脸图 + 活体 + 耗时）
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸对齐", description = "detect→crop→liveness→align，返回 5 点仿射旋转后的对齐人脸图")
    @PostMapping(value = "/align", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReturnResult<?> align(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            FaceAlignResult r = faceService.align(file.getBytes());
            if (r == null) {
                return ReturnResult.ok(Map.of("matched", false));
            }
            // alignedFace 在「未能对齐」时可能为 null，而 Map.of 会拒绝 null 值 → 用容忍 null 的 Map
            Map<String, Object> body = new LinkedHashMap<>(7);
            body.put("matched", true);
            body.put("box", r.box());
            body.put("alignedFace", r.alignedFace());
            body.put("live", r.live());
            body.put("liveScore", r.liveScore());
            body.put("elapsedMs", r.elapsedMs());
            return ReturnResult.ok(body);
        } catch (Exception e) {
            log.error("[Face] 人脸对齐失败", e);
            return ReturnResult.error("人脸对齐失败: " + e.getMessage());
        }
    }

    /**
     * 1:1 人脸比对。
     *
     * @param fileA 图片 A
     * @param fileB 图片 B
     * @return 相似度与判定
     * @param @RequestParam("fileA" @请求参数("文件a"
     */
    @Operation(summary = "人脸比对", description = "1:1 人脸比对（detect→crop→liveness→align→feature→cosine），返回相似度与是否同一人")
    @PostMapping("/compare")
    public ReturnResult<?> compare(@RequestParam("fileA") MultipartFile fileA,
                                   @RequestParam("fileB") MultipartFile fileB) {
        if (fileA.isEmpty() || fileB.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            FaceCompareResult r = faceService.compareWithMeta(fileA.getBytes(), fileB.getBytes());
            if (r == null) {
                return ReturnResult.error("人脸比对失败：任一侧未检测到人脸");
            }
            return ReturnResult.ok(Map.of(
                    "score", r.score(),
                    "matched", r.matched(),
                    "a", Map.of(
                            "box", r.a().box(),
                            "live", r.a().live(),
                            "liveScore", r.a().liveScore(),
                            "dim", r.a().dim()
                    ),
                    "b", Map.of(
                            "box", r.b().box(),
                            "live", r.b().live(),
                            "liveScore", r.b().liveScore(),
                            "dim", r.b().dim()
                    ),
                    "elapsedMs", r.elapsedMs()
            ));
        } catch (Exception e) {
            log.error("[Face] 人脸比对失败", e);
            return ReturnResult.error("人脸比对失败: " + e.getMessage());
        }
    }

    /**
     * 人脸登记入库（1:N）。
     *
     * @param id       人脸 标识
     * @param file     图片文件
     * @param metadata 附加元数据（JSON）
     * @return 登记结果
     * @param @RequestParam("id" @请求参数("标识"
     */
    @Operation(summary = "人脸登记（图像）", description = "登记管线：detect→crop→liveness→align→feature→入库；"
            + "底库只支持单人脸，检出多于一张时拒绝并返回原因")
    @PostMapping("/enroll")
    public ReturnResult<?> enroll(@RequestParam("id") String id,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "metadata", required = false) String metadata) {
        if (id == null || id.isBlank()) {
            return ReturnResult.error("人脸 ID 不能为空");
        }
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            Map<String, Object> meta = parseMetadata(metadata);
            boolean success = faceService.enrollPipeline(id, file.getBytes(), meta);
            return success ? ReturnResult.ok("登记成功") : ReturnResult.error("登记失败");
        } catch (MultiFaceEnrollException e) {
            // 可预期的业务分支：多脸照片。不打 ERROR 全栈（不是故障），只回明确原因
            log.info("[Face] 拒绝多脸登记: id={} -> {}", id, e.getMessage());
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Face] 人脸登记失败", e);
            return ReturnResult.error("人脸登记失败: " + e.getMessage());
        }
    }

    /**
     * 直接特征入库（不跑管线，调用方自行提取特征后入库）。
     *
     * @param id        人脸 标识
     * @param feature   特征向量 JSON 数组
     * @param metadata  元数据 JSON
     * @return 登记结果
     * @param @RequestParam("id" @请求参数("标识"
     */
    @Operation(summary = "人脸登记（特征向量）", description = "直接用特征向量入库，不跑管线；适用已提取特征或外部导入场景")
    @PostMapping("/enroll-feature")
    public ReturnResult<?> enrollFeature(@RequestParam("id") String id,
                                         @RequestParam("feature") String feature,
                                         @RequestParam(value = "metadata", required = false) String metadata) {
        if (id == null || id.isBlank()) {
            return ReturnResult.error("人脸 ID 不能为空");
        }
        try {
            float[] vector = parseFeature(feature);
            Map<String, Object> meta = parseMetadata(metadata);
            boolean success = faceService.enrollFeature(id, vector, meta);
            return success ? ReturnResult.ok("登记成功") : ReturnResult.error("登记失败");
        } catch (Exception e) {
            log.error("[Face] 特征入库失败", e);
            return ReturnResult.error("特征入库失败: " + e.getMessage());
        }
    }

    /**
     * 1:N 人脸识别。
     *
     * @param file 图片文件
     * @return 识别结果（TopK 命中）
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸识别", description = "1:N 人脸识别，返回 TopK 命中")
    @PostMapping("/identify")
    public ReturnResult<?> identify(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            FaceIdentifyHit hit = faceService.identifyPipeline(file.getBytes());
            if (hit == null) {
                return ReturnResult.ok(Map.of("matched", false, "hits", List.of()));
            }
            // 必须用容忍 null 的 Map：库为空时 hit.bestId() 是 null，
            // 而 Map.of(...) **拒绝 null 值**，会直接抛 NPE ——
            // 表现为「新账号/空库第一次识别就报 人脸识别失败: null」。
            Map<String, Object> body = new LinkedHashMap<>(4);
            body.put("matched", hit.bestId() != null);
            body.put("bestId", hit.bestId());
            body.put("bestScore", hit.bestScore());
            body.put("hits", hit.hits() == null ? List.of() : hit.hits());
            return ReturnResult.ok(body);
        } catch (Exception e) {
            log.error("[Face] 人脸识别失败", e);
            return ReturnResult.error("人脸识别失败: " + e.getMessage());
        }
    }

    /**
     * 1:N 特征检索（GET，query 传参）。
     *
     * <p><b>大向量请用 POST 重载。</b>512 维特征的 JSON 文本约 6.8KB，经 URL 编码后
     * 超过 Tomcat 默认 {@code maxHttpRequestHeaderSize=8192}，会被容器以
     * 400 + 默认 HTML 错误页拒绝，**连本方法的业务信封都到不了**。
     * 实测：真实全精度特征(6778B) -> 400；低位精度文本(3072B) -> 200。</p>
     *
     * @param feature 特征向量（JSON 数组）
     * @param topK    topk
     * @return 检索结果
     * @param @RequestParam("feature" @请求参数("特征"
     */
    @Operation(summary = "人脸检索(GET)", description = "按特征向量检索人脸库；向量较大时请用 POST 重载")
    @GetMapping("/search")
    public ReturnResult<?> search(@RequestParam("feature") String feature,
                                  @RequestParam(value = "topK", defaultValue = "5") int topK) {
        return doSearch(feature, topK);
    }

    /**
     * 1:N 特征检索（POST，JSON body 传参）。
     *
     * <p>存在的理由：GET 版把向量放在 query string，512 维真实特征必然超限
     * （见 {@link #search(String, int)}）。本重载把向量放进请求体，不受 URI 长度限制。</p>
     *
     * <p>请求体：{@code {"feature": [0.1, 0.2, ...], "topK": 5}}；
     * {@code feature} 接受数组或字符串两种写法，{@code topK} 缺省为 5。</p>
     *
     * @param body 请求体
     * @return 检索结果
     */
    @Operation(summary = "人脸检索(POST)", description = "按特征向量检索人脸库；向量放请求体，不受 URI 长度限制")
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReturnResult<?> searchByBody(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("feature");
        // List<Double> 的 toString 恰好是 "[0.1, 0.2, ...]"，与 parseFeature 期望的格式一致
        String feature = raw == null ? "" : String.valueOf(raw);
        int topK = 5;
        Object t = body == null ? null : body.get("topK");
        if (t instanceof Number number) {
            topK = number.intValue();
        } else if (t != null && !String.valueOf(t).isBlank()) {
            try {
                topK = Integer.parseInt(String.valueOf(t).trim());
            } catch (NumberFormatException ignored) {
                // 非法 topK 用默认值，与 GET 版 defaultValue="5" 的语义一致
            }
        }
        return doSearch(feature, topK);
    }

    /**
     * 1:N 特征检索的公共实现（GET / POST 共用，避免两处各写一份 try/catch 后走偏）。
     *
     * @param feature 特征向量（JSON 数组文本）
     * @param topK    返回条数
     * @return 检索结果
     */
    private ReturnResult<?> doSearch(String feature, int topK) {
        try {
            float[] vector = parseFeature(feature);
            List<FaceSearchHit> hits = faceService.search(vector, topK);
            return ReturnResult.ok(Map.of("hits", hits));
        } catch (Exception e) {
            log.error("[Face] 人脸检索失败", e);
            return ReturnResult.error("人脸检索失败: " + e.getMessage());
        }
    }

    /**
     * 列出当前账号人脸库中已登记的条目。
     *
     * <p>改造前后端没有这个出口，前端只能把「登记簿」存在浏览器本地（见
     * {@code useFaceRegistry.ts}）—— 于是同一份服务端人脸的档案在不同浏览器/账号下
     * 看到的并不一致。这里从 DB 档案读回来（而不是从 {@code index.json} 快照）。</p>
     *
     * <p><b>分页：</b>带 {@code page}（&ge;1）与 {@code size}（&ge;1）时服务端分页 ——
     * 万级底库不再整包拉回浏览器；不带时返回全量（兼容既有调用方，如以图搜图登记簿）。
     * {@code keyword} / {@code status} 两种模式都支持，筛选口径与前端一致。</p>
     *
     * @param page      页码（从 1 开始）；不传或 &le;0 返回全部命中
     * @param size      每页条数；不传或 &le;0 返回全部命中（上限 500，防一次拉太大）
     * @param keyword   关键词（匹配 id 与元数据字符串字段，大小写不敏感）
     * @param status    状态筛选：{@code thumb}=有图、{@code noThumb}=无图
     * @param sortField 排序字段：{@code id} / {@code hasImage} / 或元数据键（name/residence/phone/idCard…）
     * @param sortOrder 排序方向：{@code asc} / {@code desc}
     * @return 已登记条目 {@code items}（id + 元数据，不含向量）+ {@code total}（命中总数）
     *         + {@code stats}（全库统计 total/withImage/withIdCard，不受筛选影响）
     */
    @Operation(summary = "人脸库列表", description = "列出当前账号人脸库中已登记的条目；带 page/size 时服务端分页，不带返回全量")
    @GetMapping("/list")
    public ReturnResult<?> list(@RequestParam(value = "page", required = false) Integer page,
                                @RequestParam(value = "size", required = false) Integer size,
                                @RequestParam(value = "keyword", required = false) String keyword,
                                @RequestParam(value = "status", required = false) String status,
                                @RequestParam(value = "sortField", required = false) String sortField,
                                @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        try {
            boolean paged = page != null && page > 0 && size != null && size > 0;
            int cap = size == null ? 0 : Math.min(size, 500);
            return ReturnResult.ok(faceService.listPage(keyword, status, sortField, sortOrder,
                    paged ? page : 0, paged ? cap : 0));
        } catch (Exception e) {
            log.error("[Face] 人脸库列表失败", e);
            return ReturnResult.error("人脸库列表失败: " + e.getMessage());
        }
    }

    /**
     * 从当前账号人脸库删除一条（向量索引与快照同步删除）。
     *
     * @param id 人脸 标识
     * @return 是否删除成功
     */
    @Operation(summary = "删除人脸", description = "从当前账号人脸库删除一条记录")
    @DeleteMapping("/{id}")
    public ReturnResult<?> delete(@PathVariable("id") String id) {
        try {
            return ReturnResult.ok(faceService.remove(id));
        } catch (Exception e) {
            log.error("[Face] 人脸删除失败", e);
            return ReturnResult.error("人脸删除失败: " + e.getMessage());
        }
    }

    /**
     * 读一条人脸**登记时落存的裁剪图**（PNG）。
     *
     * <p>给底库「看图 / 画人脸特征点」用。返回二进制而非统一信封 ——
     * 前端直接当图片地址用。没有图（改造前登记的老数据、或登记时裁剪失败）时回 404，
     * 前端据此回落到本地缩略图/占位图，不算错误。</p>
     *
     * <p>与 {@code superResolution} 同一条口径：<b>不要</b>在映射上加
     * {@code produces = MediaType.IMAGE_PNG_VALUE}，否则 404 写出阶段会按 image/png
     * 选错转换器；类型在 {@link ResponseEntity} 上给。</p>
     *
     * @param id 人脸标识
     * @return PNG 字节；无则 404
     */
    @Operation(summary = "人脸登记图", description = "登记时落存的人脸裁剪图（PNG），供底库展示与特征点绘制")
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable("id") String id) {
        try {
            byte[] bytes = faceService.faceImage(id);
            if (bytes == null || bytes.length == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(bytes);
        } catch (Exception e) {
            log.warn("[Face] 读登记图失败: id={} -> {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 读一条人脸**登记时的原图**。
     *
     * <p>特征点对话框画点必须用原图：关键点是在原图坐标系里算的，画在裁剪脸上
     * 只会得到「已经摆正的五个点」，模型偏没偏都看不出来，这个功能就等于没做。
     * 改造前只落存了裁剪脸，所以一直没有这个接口。</p>
     *
     * <p>改造前登记的老数据没有原图，回 404；前端据此回退到 {@code /{id}/image}
     * 的裁剪图并提示用户，不算错误。</p>
     *
     * @param id 人脸标识
     * @return 原图字节；无则 404
     */
    @Operation(summary = "人脸登记原图", description = "登记时上传的原始照片，供特征点在真实照片上标注；"
            + "改造前的旧数据无原图，返回 404")
    @GetMapping("/{id}/original")
    public ResponseEntity<byte[]> original(@PathVariable("id") String id) {
        try {
            byte[] bytes = faceService.originalImage(id);
            if (bytes == null || bytes.length == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(guessImageType(bytes))
                    .body(bytes);
        } catch (Exception e) {
            log.warn("[Face] 读登记原图失败: id={} -> {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 按字节内容猜测图片 MIME。
     *
     * <p>原图是上传时的原始字节，没有统一转码，可能是 JPEG/PNG/WebP。声明成
     * {@code image/png} 会让浏览器把 JPEG 当 PNG 解析而渲染失败，因此这里必须
     * 嗅探。嗅探不出时退回 {@code application/octet-stream}，浏览器仍能靠
     * {@code <img>} 的字节嗅探正常显示。</p>
     *
     * @param bytes 图片字节
     * @return 推断出的媒体类型
     */
    private MediaType guessImageType(byte[] bytes) {
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes)) {
            String type = java.net.URLConnection.guessContentTypeFromStream(in);
            if (type != null) {
                return MediaType.parseMediaType(type);
            }
        } catch (Exception e) {
            log.debug("[Face] 推断图片类型失败: {}", e.getMessage());
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * 更新一条人脸的元数据（姓名/年龄/性别/发型/身份证/备注等）。
     *
     * <p>与 {@code enroll} 的区别：本接口**只改档案字段**，不重跑检测、不动特征向量。
     * 「只改个名字」不该要求重新上传照片，也不该因为原照片质量被判失败。</p>
     *
     * @param id       人脸 标识
     * @param metadata 新元数据（整体替换）
     * @return 更新结果
     */
    @Operation(summary = "更新人脸信息", description = "只更新档案字段（姓名/年龄/身份证等），不重跑检测、不动特征向量")
    @PutMapping("/{id}")
    public ReturnResult<?> update(@PathVariable("id") String id,
                                  @RequestBody Map<String, Object> metadata) {
        if (id == null || id.isBlank()) {
            return ReturnResult.error("人脸 ID 不能为空");
        }
        try {
            boolean ok = faceService.updateMetadata(id, metadata);
            return ok ? ReturnResult.ok("更新成功") : ReturnResult.error("未找到该人脸：" + id);
        } catch (Exception e) {
            log.error("[Face] 人脸信息更新失败", e);
            return ReturnResult.error("人脸信息更新失败: " + e.getMessage());
        }
    }

    /**
     * 核验（人脸 标识 + 图片是否匹配）。
     *
     * @param id   人脸 标识
     * @param file 图片文件
     * @return 核验结果
     * @param @RequestParam("id" @请求参数("标识"
     */
    @Operation(summary = "人脸核验", description = "核验指定人脸 ID 与图片是否匹配")
    @PostMapping("/verify")
    public ReturnResult<?> verify(@RequestParam("id") String id,
                                  @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            boolean matched = faceService.verify(id, file.getBytes());
            return ReturnResult.ok(Map.of("matched", matched));
        } catch (Exception e) {
            log.error("[Face] 人脸核验失败", e);
            return ReturnResult.error("人脸核验失败: " + e.getMessage());
        }
    }

    /**
     * 活体检测。
     *
     * @param file 裁剪后人脸图片
     * @return 活体判定
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "活体检测", description = "检测人脸是否为活体")
    @PostMapping("/liveness")
    public ReturnResult<?> liveness(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            boolean live = faceService.isLive(file.getBytes());
            float score = faceService.liveScore(file.getBytes());
            return ReturnResult.ok(Map.of("live", live, "score", score));
        } catch (Exception e) {
            log.error("[Face] 活体检测失败", e);
            return ReturnResult.error("活体检测失败: " + e.getMessage());
        }
    }

    /**
     * 动漫人脸检测。
     *
     * @param file 图片文件
     * @return 动漫人脸检测结果
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "动漫人脸检测", description = "检测动漫/二次元图片中的人脸")
    @PostMapping("/anime-detect")
    public ReturnResult<?> animeDetect(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            List<DetectionInfo> results = faceService.animeDetect(file.getBytes());
            return ReturnResult.ok(Map.of("count", results.size(), "faces", results));
        } catch (ApiException e) {
            // 能力未配置是**可预期业务状态**，不是程序错误：只记一行 WARN，不打异常栈。
            // 契约不变（fg.sh §10.5：可选能力关闭时 200 + 结构完整信封）。
            log.warn("[Face] 动漫人脸检测未配置: {}", e.getMessage());
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Face] 动漫人脸检测失败", e);
            return ReturnResult.error("动漫人脸检测失败: " + e.getMessage());
        }
    }

    /**
     * 人脸超分。
     *
     * <p>注意：**不要**给这个映射加 {@code produces = MediaType.IMAGE_PNG_VALUE}。</p>
     *
     * <p>加过，代价是错误响应永远发不出去：端点声明只产出 image/png 后，
     * 失败时 {@code ResponseEntity.badRequest()} 的写出阶段会按 image/png 选到
     * 字节数组转换器；连 {@code ApiExceptionAdvice} 返回的 JSON 信封也被它拿去写，
     * 于是抛 {@code ClassCastException: ReturnResult cannot be cast to [B}，
     * 把本该是 400 的业务错误升级成 500。成功路径本来就显式设了
     * {@code .contentType(MediaType.IMAGE_PNG)}，这个 produces 是多余且有害的。</p>
     *
     * @param file 图片文件
     * @return 超分后图片
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸超分", description = "GFPGAN/ESRGAN 人脸超分辨率")
    @PostMapping("/super-resolution")
    public ResponseEntity<byte[]> superResolution(@RequestParam("file") MultipartFile file) {
        try {
            byte[] result = faceService.superResolution(file.getBytes());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(result);
        } catch (Exception e) {
            log.error("[Face] 人脸超分失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 人脸修复。
     *
     * @param file 图片文件
     * @return 修复后图片
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸修复", description = "CodeFormer 人脸修复（老照片/模糊人脸）")
    @PostMapping("/restore")
    public ResponseEntity<byte[]> restore(@RequestParam("file") MultipartFile file) {
        try {
            byte[] result = faceService.restore(file.getBytes());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(result);
        } catch (ApiException e) {
            // 「restore_model 未配置」是可预期状态：WARN 一行代替 ERROR + 全栈
            // （验收报告 §7.5 记录的正是修复前的 NPE 噪声）。
            // 状态码不变：fg.sh §10.6 要求二进制端点未配置模型时 400，不得 5xx。
            log.warn("[Face] 人脸修复未配置: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[Face] 人脸修复失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 人脸属性分析。
     *
     * @param file 图片文件
     * @return 年龄/性别/种族
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸属性分析", description = "年龄/性别/种族属性识别")
    @PostMapping("/attributes")
    public ReturnResult<?> attributes(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            String result = faceService.analyzeAttributes(file.getBytes());
            return ReturnResult.ok(Map.of("attributes", result));
        } catch (ApiException e) {
            log.warn("[Face] 人脸属性分析未配置: {}", e.getMessage());
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Face] 人脸属性分析失败", e);
            return ReturnResult.error("人脸属性分析失败: " + e.getMessage());
        }
    }

    /**
     * 人脸表情识别。
     *
     * @param file 图片文件
     * @return 表情标签
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸表情识别", description = "识别图片中的人脸表情")
    @PostMapping("/emotion")
    public ReturnResult<?> emotion(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            String result = faceService.analyzeEmotion(file.getBytes());
            return ReturnResult.ok(Map.of("emotion", result));
        } catch (ApiException e) {
            log.warn("[Face] 人脸表情识别未配置: {}", e.getMessage());
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Face] 人脸表情识别失败", e);
            return ReturnResult.error("人脸表情识别失败: " + e.getMessage());
        }
    }

    /**
     * 人脸关键点。
     *
     * @param file 图片文件
     * @return 关键点坐标（106 点）
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸关键点", description = "人脸 106 点关键点定位")
    @PostMapping("/landmark")
    public ReturnResult<?> landmark(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            float[] points = faceService.landmark(file.getBytes());
            return ReturnResult.ok(Map.of("points", points));
        } catch (ApiException e) {
            log.warn("[Face] 人脸关键点未配置: {}", e.getMessage());
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Face] 人脸关键点失败", e);
            return ReturnResult.error("人脸关键点失败: " + e.getMessage());
        }
    }

    /**
     * 人脸关键点管线（坐标已映射回**原图像素**）。
     *
     * <p>与 {@code /landmark} 的差异：{@code /landmark} 是把整图直接喂关键点模型，
     * 返回相对模型输入图的归一化坐标（±1），前端画不到原图上；本端点先按检测框裁出人脸、
     * 再取点并映射回原图，返回的 {@code points} 可直接叠加绘制。</p>
     *
     * @param file 图片文件
     * @return 关键点（原图像素）+ 人脸框 + 计数
     */
    @Operation(summary = "人脸关键点管线", description = "检测人脸→按框裁剪→关键点，坐标映射回原图像素")
    @PostMapping("/landmark-pipeline")
    public ReturnResult<?> landmarkPipeline(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            FaceLandmarkResult r = faceService.landmarkPipeline(file.getBytes());
            if (r == null) {
                return ReturnResult.ok(Map.of("matched", false, "points", List.of(), "count", 0));
            }
            // 必须用容忍 null 的 Map（points 为空时为 List.of()，box 也可能为 null）
            Map<String, Object> body = new LinkedHashMap<>(8);
            body.put("matched", true);
            body.put("box", r.box());
            body.put("points", r.points());
            body.put("count", r.count());
            body.put("signed", r.signed());
            body.put("elapsedMs", r.elapsedMs());
            return ReturnResult.ok(body);
        } catch (ApiException e) {
            log.warn("[Face] 人脸关键点未配置: {}", e.getMessage());
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Face] 人脸关键点失败", e);
            return ReturnResult.error("人脸关键点失败: " + e.getMessage());
        }
    }

    /**
     * 人脸质量评估。
     *
     * @param file 图片文件
     * @return 质量评估结果
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "人脸质量评估", description = "评估人脸图片质量（模糊/亮度/人脸占比）")
    @PostMapping("/quality")
    public ReturnResult<?> quality(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            FaceQualityInfo info = faceService.quality(file.getBytes());
            if (info == null) {
                return ReturnResult.ok(Map.of("enabled", false));
            }
            return ReturnResult.ok(Map.of(
                    "enabled", true,
                    "faceCount", info.faceCount(),
                    "faceAreaRatio", info.faceAreaRatio(),
                    "blurScore", info.blurScore(),
                    "brightness", info.brightness(),
                    "faceOk", info.faceOk()
            ));
        } catch (Exception e) {
            log.error("[Face] 人脸质量评估失败", e);
            return ReturnResult.error("人脸质量评估失败: " + e.getMessage());
        }
    }

    /**
     * 换脸检测。
     *
     * @param file 图片文件
     * @return 是否疑似换脸
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @请求参数("文件"
     * @param @RequestParam("file" @RequestParam("file"
     */
    @Operation(summary = "换脸检测", description = "检测图片是否疑似 AI 换脸")
    @PostMapping("/deepfake")
    public ReturnResult<?> deepfake(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            boolean result = faceService.isDeepfake(file.getBytes());
            return ReturnResult.ok(Map.of("deepfake", result));
        } catch (ApiException e) {
            log.warn("[Face] 换脸检测未配置: {}", e.getMessage());
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Face] 换脸检测失败", e);
            return ReturnResult.error("换脸检测失败: " + e.getMessage());
        }
    }

    /**
     * 人脸模型枚举。
     *
     * @return 可用模型清单
     */
    @Operation(summary = "人脸模型清单", description = "枚举可用的人脸检测/特征/活体/超分等模型")
    @GetMapping("/models")
    public ReturnResult<?> models() {
        return ReturnResult.ok(faceService.listModels());
    }

    /**
     * 解析 JSON 数组特征向量。
     *
     * @param json JSON 数组字符串
     * @return 特征向量
     */
    private static float[] parseFeature(String json) {
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String[] parts = trimmed.substring(1, trimmed.length() - 1).split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        }
        /**
         * IllegalArgumentException。
         * @param 数组" 数组"
         * @return IllegalArgumentException的结果
         */
        throw new IllegalArgumentException("特征向量格式错误，应为 JSON 数组");
    }

    /**
     * 解析可选的 metadata JSON。
     *
     * @param json JSON 对象字符串，空或非法时返回空 映射
     * @return 元数据 映射
     */
    private static Map<String, Object> parseMetadata(String json) {
        if (null == json || json.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
