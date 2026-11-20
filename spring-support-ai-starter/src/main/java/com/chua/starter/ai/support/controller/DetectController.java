package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.DetectorUtils;
import com.chua.common.support.utils.PhotoMetadataUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 内容检测控制器。
 *
 * <p>提供文本 / 图片「是否 AI」检测执行能力：</p>
 * <ul>
 *   <li>文本：{@link DetectorUtils} 纯统计启发式评分（0~100），输出判定、置信度、
 *       逐句标注与正反依据；</li>
 *   <li>图片：{@link PhotoMetadataUtils} 元数据分析（C2PA 内容凭证 / IPTC 来源类型 /
 *       XMP / EXIF / 生成参数块），输出判定、置信度与信号清单。</li>
 * </ul>
 *
 * <p>检测内核来自 utils 层工具类，本控制器只做开关/阈值/大小限制的配置读取与编排，
 * 不重复实现检测算法。配置走 {@code detect} 分组全局设置
 * （{@code /v2/settings/detect} 读写），变更实时生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/detect")
@RequiredArgsConstructor
@Tag(name = "AI 内容检测", description = "文本 / 图片「是否 AI」检测（纯启发式与元数据分析，不依赖模型）")
public class DetectController {

    /**
     * 检测配置分组
     */
    private static final String GROUP = "detect";

    /**
     * 文本检测开关字段
     */
    private static final String FIELD_TEXT_ENABLED = "text_enabled";

    /**
     * 文本判定阈值字段
     */
    private static final String FIELD_TEXT_THRESHOLD = "text_threshold";

    /**
     * 图片检测开关字段
     */
    private static final String FIELD_IMAGE_ENABLED = "image_enabled";

    /**
     * 图片大小上限字段（MB）
     */
    private static final String FIELD_IMAGE_MAX_SIZE_MB = "image_max_size_mb";

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService settingService;

    /**
     * 文本 AI 检测。
     *
     * @param body 请求体（{@code text}：待检测文本）
     * @return report（完整检测报告）+ threshold（当前判定阈值）+ likelyAi（是否达到阈值）
     */
    @Operation(summary = "文本 AI 检测", description = "对文本做 AI 生成可能性评分（0~100），返回判定、置信度、逐句标注与依据")
    @PostMapping("/text")
    public ReturnResult<?> detectText(@RequestBody Map<String, Object> body) {
        if (!boolSetting(FIELD_TEXT_ENABLED, true)) {
            return ReturnResult.error("文本 AI 检测未启用，请在「系统设置 → AI 检测配置」中开启");
        }
        Object raw = body == null ? null : body.get("text");
        String text = raw == null ? "" : String.valueOf(raw);
        if (StringUtils.isBlank(text)) {
            return ReturnResult.error("请输入待检测的文本");
        }
        if (text.length() > DetectorUtils.SUGGESTED_MAX_CHARS * 4) {
            return ReturnResult.error("文本过长，请截取前 " + (DetectorUtils.SUGGESTED_MAX_CHARS * 4) + " 字以内再检测");
        }

        int threshold = intSetting(FIELD_TEXT_THRESHOLD, DetectorUtils.LIKELY_AI_THRESHOLD);
        DetectorUtils.DetectionResult report = DetectorUtils.detect(text);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);
        result.put("threshold", threshold);
        result.put("likelyAi", report.aiProbability() >= threshold);
        return ReturnResult.ok(result);
    }

    /**
     * 图片 AI 元数据检测。
     *
     * @param file 图片文件
     * @return report（元数据检测报告）+ fileName + size
     */
    @Operation(summary = "图片 AI 元数据检测", description = "读取图片元数据（C2PA/XMP/EXIF/生成参数块）判断是否带 AI 生成/编辑痕迹")
    @PostMapping("/image")
    public ReturnResult<?> detectImage(@RequestParam("file") MultipartFile file) {
        if (!boolSetting(FIELD_IMAGE_ENABLED, true)) {
            return ReturnResult.error("图片 AI 检测未启用，请在「系统设置 → AI 检测配置」中开启");
        }
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        long maxBytes = intSetting(FIELD_IMAGE_MAX_SIZE_MB, 20) * 1024L * 1024L;
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            return ReturnResult.error("图片超过大小上限（" + (maxBytes / 1024 / 1024) + " MB）");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (Exception e) {
            log.warn("[Detect] 读取上传图片失败: {}", e.getMessage());
            return ReturnResult.error("读取上传图片失败");
        }

        PhotoMetadataUtils.Report report = PhotoMetadataUtils.analyze(data);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);
        result.put("fileName", file.getOriginalFilename());
        result.put("size", file.getSize());
        return ReturnResult.ok(result);
    }

    /**
     * 读布尔配置。
     *
     * @param field 字段名
     * @param def   缺省值
     * @return 配置值（未配置/非法时取缺省值）
     */
    private boolean boolSetting(String field, boolean def) {
        return settingService.getValue(GROUP, field)
                .map(v -> "true".equalsIgnoreCase(v.trim()))
                .orElse(def);
    }

    /**
     * 读整数配置。
     *
     * @param field 字段名
     * @param def   缺省值
     * @return 配置值（未配置/非法时取缺省值）
     */
    private int intSetting(String field, int def) {
        try {
            return settingService.getValue(GROUP, field)
                    .map(Integer::parseInt)
                    .orElse(def);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
