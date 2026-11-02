package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.deeplearning.support.face.FaceDetector;
import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.deeplearning.support.reid.PersonTrack;
import com.chua.starter.ai.support.configuration.reid.ReidClientManager;
import com.chua.starter.ai.support.service.AiReidService;
import com.chua.starter.ai.support.service.MultiFaceEnrollException;
import com.chua.starter.ai.support.service.ReidLibraryLinkService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.util.AiLibraryGroups;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 人脸重识别控制器。
 *
 * <p>提供跨摄像头/跨帧重识别的对外接口：观测一帧（自动归并轨迹）、以图搜人、
 * 轨迹查询与清理，以及人脸组的配置管理——人脸重识别管线与人脸识别共享 {@code face}
 * 分组的模型与归并参数，不再使用第二套配置分组。</p>
 *
 * <p><b>鉴权口径：登录即可用</b>，与 {@code FaceController} 一致 ——
 * 轨迹按账号隔离（见 {@code AiReidServiceImpl}），每个账号只看到自己的观测记录。</p>
 *
 * <p>配置更新后热重建：模型与归并参数即时生效，已有轨迹会被清空
 * （归并参数变了，旧轨迹的语义不再一致）。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/reid")
@RequiredArgsConstructor
@Tag(name = "AI 人脸重识别", description = "跨摄像头/跨帧同一人脸追踪、轨迹归并、以图搜人")
public class ReidController {

    /**
     * 配置分组：人脸重识别管线与人脸识别共享 {@code face} 分组，
     * 不再新建独立的 {@code reid} 配置分组。
     */
    private static final String GROUP = "face";

    /**
     * 人脸重识别服务。
     */
    private final AiReidService reidService;

    /**
     * 重识别底库联动层（列表/删除用）。
     */
    private final ReidLibraryLinkService reidLibrary;

    /**
     * 重识别客户端管理器。
     */
    private final ReidClientManager clientManager;

    /**
     * 全局设置服务。
     */
    private final SysAiGlobalSettingService settingService;

    /**
     * 观测一帧：检测人脸并归并进跨帧轨迹。
     *
     * <p>同一个人在不同摄像头出现时会自动并入同一条轨迹；观测落底库。</p>
     *
     * @param file     图片文件
     * @param cameraId 摄像头标识（可空，用配置的默认值）
     * @param sourceId 来源帧标识（可空，自动生成）
     * @param group    分组名（可空，只查默认组）
     * @return 观测结果
     */
    @Operation(summary = "观测一帧", description = "检测图片中的人脸并归并进跨帧轨迹；同一个人自动并入同一轨迹，观测落底库")
    @PostMapping("/observe")
    public ReturnResult<?> observe(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "cameraId", required = false) String cameraId,
                                   @RequestParam(value = "sourceId", required = false) String sourceId,
                                   @RequestParam(value = "group", required = false) String group,
                                   @RequestParam(value = "lat", required = false) java.math.BigDecimal lat,
                                   @RequestParam(value = "lng", required = false) java.math.BigDecimal lng) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            return ReturnResult.ok(reidService.observe(group, cameraId, sourceId, file.getBytes(), lat, lng));
        } catch (IOException e) {
            log.warn("[Reid] 读取上传文件失败: {}", e.getMessage());
            return ReturnResult.error("读取上传文件失败");
        }
    }

    /**
     * 重识别登记入库（把一张照片登记入重识别底库）。
     *
     * <p>登记管线：detect → crop → liveness → align → feature → 写
     * {@code sys_ai_reid_library} + 重识别向量库。</p>
     *
     * <p><b>底库只支持单人脸</b>，检出多于一张时拒绝并返回原因</p>
     *
     * @param group    分组名；不传/空 = 默认组 default
     * @param id       人员标识（底库行的向量标识）
     * @param file     图片文件
     * @param metadata 附加元数据（JSON）
     * @return 登记结果
     */
    @Operation(summary = "重识别登记", description = "登记管线：detect→crop→liveness→align→feature→入库；底库只支持单人脸，检出多于一张时拒绝并返回原因")
    @PostMapping("/enroll")
    public ReturnResult<?> enroll(@RequestParam(value = "group", required = false) String group,
                                  @RequestParam("id") String id,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "metadata", required = false) String metadata) {
        if (id == null || id.isBlank()) {
            return ReturnResult.error("人员 ID 不能为空");
        }
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            Map<String, Object> meta = parseMetadata(metadata);
            boolean success = reidService.enroll(group, id, file.getBytes(), meta);
            return success ? ReturnResult.ok("已登记") : ReturnResult.error("登记失败");
        } catch (MultiFaceEnrollException e) {
            // 可预期的业务分支：多脸照片。不打 ERROR 全栈（不是故障），只回明确原因
            log.info("[Reid] 拒绝多脸登记: id={} -> {}", id, e.getMessage());
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Reid] 重识别登记失败", e);
            return ReturnResult.error("重识别登记失败: " + e.getMessage());
        }
    }

    /**
     * 以图搜人：在已有轨迹中检索最相似的人。
     *
     * @param file  图片文件
     * @param topK  返回条数（可空，用配置值）
     * @param group 分组名（可空，只查默认组；逗号分隔可查多个分组）
     * @return 命中列表
     */
    @Operation(summary = "以图搜人", description = "在已有轨迹中检索最相似的人，返回轨迹与相似度")
    @PostMapping("/search")
    public ReturnResult<?> search(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "topK", required = false) Integer topK,
                                  @RequestParam(value = "group", required = false) String group) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        try {
            List<AiReidService.ReidHit> hits = reidService.search(
                    AiLibraryGroups.parse(group), file.getBytes(), topK == null ? 0 : topK);
            return ReturnResult.ok(Map.of("count", hits.size(), "hits", hits));
        } catch (IOException e) {
            log.warn("[Reid] 读取上传文件失败: {}", e.getMessage());
            return ReturnResult.error("读取上传文件失败");
        }
    }

    /**
     * 列出轨迹。
     *
     * @param group     分组名（可空，只查默认组）
     * @param cameraId  只看某个摄像头的轨迹（可空）
     * @param crossOnly 只看跨摄像头轨迹（可空）
     * @param limit     返回上限（可空）
     * @return 轨迹列表
     */
    @Operation(summary = "轨迹列表", description = "从重识别底库汇聚出当前账号的跨帧轨迹；可按分组/摄像头过滤或只看跨镜轨迹")
    @GetMapping("/tracks")
    public ReturnResult<?> tracks(@RequestParam(value = "group", required = false) String group,
                                  @RequestParam(value = "cameraId", required = false) String cameraId,
                                  @RequestParam(value = "crossOnly", required = false) Boolean crossOnly,
                                  @RequestParam(value = "limit", required = false) Integer limit) {
        List<AiReidService.Track> tracks = reidService.tracks(
                group, cameraId, Boolean.TRUE.equals(crossOnly), limit == null ? 0 : limit);
        return ReturnResult.ok(Map.of("count", tracks.size(), "tracks", tracks));
    }

    /**
     * 轨迹详情。
     *
     * @param group    分组名（可空，只查默认组）
     * @param trackId  轨迹 ID
     * @return 轨迹
     */
    @Operation(summary = "轨迹详情", description = "按轨迹 ID 查询轨迹的摄像头集合、观测次数、时间跨度与位置")
    @GetMapping("/tracks/{trackId}")
    public ReturnResult<?> track(@RequestParam(value = "group", required = false) String group,
                                 @PathVariable("trackId") String trackId) {
        AiReidService.Track track = reidService.track(group, trackId);
        if (track == null) {
            return ReturnResult.error("轨迹不存在");
        }
        return ReturnResult.ok(track);
    }

    /**
     * 重识别底库列表（观测记录，供设置中心的「重识别库」面板）。
     *
     * @param group 分组名（可空，只查默认组）
     * @return 底库观测列表
     */
    @Operation(summary = "重识别底库列表", description = "列出当前账号指定分组的全部观测记录（含摄像头、轨迹、经纬度）")
    @GetMapping("/library")
    public ReturnResult<?> library(@RequestParam(value = "group", required = false) String group) {
        String account = AiUserContext.requireUserId();
        java.util.List<Map<String, Object>> rows = reidLibrary.listMine(account, AiLibraryGroups.parse(group));
        return ReturnResult.ok(Map.of("count", rows.size(), "items", rows));
    }

    /**
     * 删除一条观测记录（同步删向量）。
     *
     * @param id    观测记录 ID（即向量标识）
     * @param group 分组名（可空，只查默认组）
     * @return 是否成功
     */
    @Operation(summary = "删除重识别观测", description = "按记录 ID 删除一条观测（同步删除其向量）")
    @DeleteMapping("/library/{id}")
    public ReturnResult<?> deleteLibraryItem(@PathVariable("id") String id,
                                             @RequestParam(value = "group", required = false) String group) {
        String account = AiUserContext.requireUserId();
        boolean removed = reidLibrary.deleteMeta(account, id);
        return removed ? ReturnResult.ok("已删除") : ReturnResult.error("删除失败或记录不存在");
    }

    /**
     * 轨迹统计。
     *
     * @param group 分组名（可空，只查默认组）
     * @return 统计信息
     */
    @Operation(summary = "轨迹统计", description = "当前账号指定分组的轨迹总数与重识别能力状态")
    @GetMapping("/stats")
    public ReturnResult<?> stats(@RequestParam(value = "group", required = false) String group) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("available", reidService.available());
        stats.put("tracks", reidService.trackCount(group));
        stats.put("config", clientManager.configSummary());
        return ReturnResult.ok(stats);
    }

    /**
     * 清空指定分组的全部轨迹。
     *
     * @param group 分组名（可空，只清默认组）
     * @return 清空的轨迹数
     */
    @Operation(summary = "清空轨迹", description = "清空当前账号指定分组的全部跨帧轨迹")
    @DeleteMapping("/tracks")
    public ReturnResult<?> clear(@RequestParam(value = "group", required = false) String group) {
        return ReturnResult.ok(Map.of("cleared", reidService.clear(group)));
    }

    /**
     * 当前账号已有数据的分组名列表（供前端分组选择器）。
     *
     * @return 分组名列表
     */
    @Operation(summary = "分组列表", description = "返回当前账号已有数据的分组名，供前端分组选择器渲染")
    @GetMapping("/groups")
    public ReturnResult<?> groups() {
        return ReturnResult.ok(Map.of("groups", reidService.groups()));
    }

    /**
     * 获取人脸组配置项定义与当前值。
     *
     * <p>人脸重识别管线与人脸识别共享 {@code face} 分组（检测/特征模型、归并阈值、
     * 时间窗、容量、保留时长、向量库与特征维度都在同一组），
     * 此处暴露的即是该组的全部字段。</p>
     *
     * @return 配置列表（field/value/valueType/description/sort）
     */
    @Permission("admin")
    @Operation(summary = "获取重识别配置", description = "返回 face 分组全部配置项及当前值（重识别管线与人脸识别共享同一组）")
    @GetMapping("/config")
    public ReturnResult<List<Map<String, Object>>> config() {
        List<Map<String, Object>> fields = new ArrayList<>();

        List<Map<String, Object>> current = settingService.list(GROUP);
        Map<String, Object> valueMap = current.stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("field")), m -> m, (a, b) -> a));

        List<GlobalSettingProvider> providers = ServiceProvider.of(GlobalSettingProvider.class).collect();
        for (GlobalSettingProvider provider : providers) {
            if (!GROUP.equals(provider.group())) {
                continue;
            }
            for (GlobalSettingProvider.SettingItem item : provider.getItems()) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("field", item.field());
                field.put("valueType", item.valueType());
                field.put("description", item.description());
                field.put("sort", item.sort());
                Map<String, Object> existing = (Map<String, Object>) valueMap.get(item.field());
                field.put("value", existing != null ? existing.get("value") : item.defaultValue());
                fields.add(field);
            }
        }

        fields.sort((a, b) -> Integer.compare(toInt(a.get("sort")), toInt(b.get("sort"))));
        return ReturnResult.ok(fields);
    }

    /**
     * 更新重识别配置（逐字段写入人脸组，触发热重建）。
     *
     * <p><b>不用 {@code replace} 整组替换。</b>整组替换在缺字段/类型不符时会抛
     * {@code S0512C0000 数据处理异常}（实测），且会把未提交的字段一并清空。
     * 这里改成与 {@code SettingsController} 相同的做法：逐字段
     * {@code upsertValue} —— 只写提交了的键，其它保持原值。</p>
     *
     * <p>写的是<b>全局层</b>：{@code ReidClientManager} 是共享单例，只读全局配置，
     * 写用户覆盖层会「保存成功却不生效」。</p>
     *
     * @param body 配置请求（字段集合；可带 {@code fields} 包一层，也可直接是键值对）
     * @return 更新结果
     */
    @Permission("admin")
    @Operation(summary = "更新重识别配置", description = "逐字段写入 face 全局配置并触发热重建（已有实时归并状态会被重置）")
    @PutMapping("/config")
    public ReturnResult<?> update(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.getOrDefault("fields", body);
        if (!(raw instanceof Map<?, ?> fields) || fields.isEmpty()) {
            return ReturnResult.error("没有可更新的配置项");
        }
        int updated = 0;
        for (Map.Entry<?, ?> entry : fields.entrySet()) {
            String field = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            settingService.upsertValue(GROUP, field, String.valueOf(value), null);
            updated++;
        }
        log.info("[ReidConfig] face 分组已更新 {} 项配置，热重建生效", updated);
        return ReturnResult.ok("人脸重识别配置已更新（" + updated + " 项）");
    }

    /**
     * 可用模型清单（检测模型 + 特征模型）。
     *
     * @return 模型清单
     */
    @Permission("admin")
    @Operation(summary = "可用模型清单", description = "枚举可用的人脸检测模型与人脸特征模型")
    @GetMapping("/config/models")
    public ReturnResult<Map<String, List<String>>> models() {
        Map<String, List<String>> models = new LinkedHashMap<>();
        models.put("detector", FaceDetector.listModels());
        models.put("feature", FeatureExtractor.listModels());
        return ReturnResult.ok(models);
    }

    /**
     * 安全转换整数。
     *
     * @param obj 值
     * @return int，非法返回 0
     */
    private static int toInt(Object obj) {
        if (obj instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 解析可选的 metadata JSON（登记用）。
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