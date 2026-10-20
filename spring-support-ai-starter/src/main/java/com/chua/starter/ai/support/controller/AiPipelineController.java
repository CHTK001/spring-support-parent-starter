package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.model.PipelineDetailDTO;
import com.chua.starter.ai.support.model.PipelineModelDTO;
import com.chua.starter.ai.support.service.settings.AiModelService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 管线目录控制器。
 *
 * <p>返回系统内置 AI 管线目录（文生图 / 文生视频 / OCR / 布局 / 人脸识别 / 图片识别），
 * 以及全局 Bean 设置分组（对话路由 / RAG / 输入优化等）。
 * 每个管线声明其必填能力（required）与非必填能力（optional）——按后台管线定义，
 * 必填与非必填的模型均属于该管线的模型集合，前端据此在模型管理中打标并分组展示。</p>
 *
 * <p>同一份目录挂三个前缀：{@code /v2/ai/pipelines}（内部规范路径）、
 * {@code /v2/models/pipelines}（模型管理页取目录）与 {@code /v2/pipelines}（能力页取详情与成员模型）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping({"/v2/ai/pipelines", "/v2/models/pipelines", "/v2/pipelines"})
@RequiredArgsConstructor
@Tag(name = "AI 管线目录")
public class AiPipelineController {

    private final AiModelService modelService;

    /**
     * 管线目录。
     *
     * @return 管线定义列表
     */
    @Permission("admin")
    @Operation(summary = "AI 管线目录", description = "管线必填/非必填能力定义，驱动左侧菜单、能力页与模型管理")
    @GetMapping
    public ReturnResult<List<Map<String, Object>>> pipelines() {
        return ReturnResult.ok(catalog());
    }

    /**
     * 单条管线详情（能力页据此渲染必填/非必填能力）。
     *
     * @param code 管线编码
     * @return 管线详情
     */
    @Permission("admin")
    @Operation(summary = "AI 管线详情", description = "按编码返回单条管线的必填/非必填能力定义")
    @GetMapping("/{code}")
    public ReturnResult<PipelineDetailDTO> pipelineDetail(@PathVariable String code) {
        return ReturnResult.ok(toDetail(catalog().stream()
                .filter(item -> code.equals(item.get("code")))
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "管线不存在: " + code))));
    }

    /**
     * 管线成员模型：能力的必填/非必填归属由模型自身的 requiredPipelines / optionalPipelines 决定。
     *
     * @param code 管线编码
     * @return 成员模型列表
     */
    @Permission("admin")
    @Operation(summary = "AI 管线成员模型", description = "声明了该管线的模型，required 标记必填与否")
    @GetMapping("/{code}/models")
    public ReturnResult<List<PipelineModelDTO>> pipelineModels(@PathVariable String code) {
        Map<String, Object> definition = catalog().stream()
                .filter(item -> code.equals(item.get("code")))
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "管线不存在: " + code));
        List<PipelineModelDTO> result = new ArrayList<>();
        for (AiModelDTO model : modelService.list()) {
            boolean required = contains(model.getRequiredPipelines(), code);
            boolean optional = !required && contains(model.getOptionalPipelines(), code);
            List<String> declaredRequired = declaredCapabilities(definition, "required");
            List<String> declaredOptional = declaredCapabilities(definition, "optional");
            required = required || intersects(model.getCapabilities(), declaredRequired);
            optional = optional || (!required && intersects(model.getCapabilities(), declaredOptional));
            if (!required && !optional) {
                continue;
            }
            result.add(new PipelineModelDTO(
                    model.getId(),
                    model.getName(),
                    model.getProviderName(),
                    matchCapability(model.getCapabilities(), definition),
                    model.getEnabled(),
                    required));
        }
        return ReturnResult.ok(result);
    }

    /**
     * 取管线定义中某一侧（required / optional）声明的能力列表。
     *
     * @param definition 管线定义
     * @param key        字段名
     * @return 能力列表，缺失时为空列表
     */
    @SuppressWarnings("unchecked")
    private static List<String> declaredCapabilities(Map<String, Object> definition, String key) {
        Object value = definition.get(key);
        return value instanceof List ? (List<String>) value : List.of();
    }

    /**
     * 两个能力列表是否有交集。
     *
     * @param values     模型能力，可为 {@code null}
     * @param declared   管线声明的能力
     * @return 是否命中
     */
    private static boolean intersects(List<String> values, List<String> declared) {
        return values != null && values.stream().anyMatch(declared::contains);
    }

    /**
     * 取模型能力中属于该管线声明的那一个，用于表格「能力」列展示。
     *
     * @param capabilities 模型能力列表
     * @param definition   管线定义
     * @return 命中的能力，无命中时为 {@code null}
     */
    private static String matchCapability(List<String> capabilities, Map<String, Object> definition) {
        if (capabilities == null || capabilities.isEmpty()) {
            return null;
        }
        return capabilities.stream()
                .filter(declaredCapabilities(definition, "required")::contains)
                .findFirst()
                .orElseGet(() -> capabilities.stream()
                        .filter(declaredCapabilities(definition, "optional")::contains)
                        .findFirst()
                        .orElse(capabilities.get(0)));
    }

    /**
     * 列表判空包含。
     *
     * @param values 列表，可为 {@code null}
     * @param value  待查值
     * @return 是否包含
     */
    private static boolean contains(List<String> values, String value) {
        return values != null && values.contains(value);
    }

    /**
     * 目录条目转详情 DTO（同时给出前端字段名的能力别名）。
     *
     * @param definition 目录条目
     * @return 管线详情
     */
    @SuppressWarnings("unchecked")
    private static PipelineDetailDTO toDetail(Map<String, Object> definition) {
        List<String> required = (List<String>) definition.get("required");
        List<String> optional = (List<String>) definition.get("optional");
        return new PipelineDetailDTO(
                (String) definition.get("code"),
                (String) definition.get("name"),
                (String) definition.get("group"),
                (String) definition.get("tag"),
                required,
                optional,
                required,
                optional,
                (String) definition.get("description"),
                (String) definition.get("route"),
                (String) definition.get("configGroup"),
                (String) definition.get("kind"));
    }

    /**
     * 构造管线目录（唯一数据源）。
     *
     * @return 管线与全局 Bean 定义列表
     */
    private static List<Map<String, Object>> catalog() {
        List<Map<String, Object>> result = new ArrayList<>();

        result.add(pipeline("text-to-image", "文生图", "generate", "text-to-image",
                List.of("text-to-image"), List.of("image-generate"),
                "基于提示词生成图像，全局 ImageClient 会话由 generate 分组驱动", "/capabilities/text-to-image", "generate"));
        result.add(pipeline("text-to-video", "文生视频", "generate", "text-to-video",
                List.of("text-to-video"), List.of(),
                "基于提示词生成视频，全局 VideoClient 会话由 generate 分组驱动", "/capabilities/text-to-video", "generate"));
        result.add(pipeline("ocr", "OCR 文字识别", "ocr", "ocr",
                List.of("ocr"), List.of("layout"),
                "文字检测→矫正→识别管线，检测/识别模型必填，方向/超分/文本矫正模型可选（非必填，留空关闭）", "/capabilities/ocr", "ocr"));
        result.add(pipeline("layout", "版面布局分析", "layout", "layout",
                List.of("layout"), List.of("ocr"),
                "文档版面分析，识别标题/表格/图片等区域", "/capabilities/layout", "layout"));
        result.add(pipeline("face", "人脸识别", "face", "face",
                List.of("detect", "face-recognize"), List.of("eye-detect", "smile-detect", "face-quality", "liveness"),
                "人脸检测+识别管线，检测/识别模型必填，眼睛/微笑/质量/活体可选", "/capabilities/face", "face"));
        result.add(pipeline("image", "图片识别", "image", "image",
                List.of("classify", "image-detect"), List.of("segment", "enhance", "captioning", "depth",
                        "image-quality", "pedestrian", "plate-detect", "plate-recognize", "feature"),
                "图像分类/目标检测管线，分类与检测模型必填，分割/增强/描述等可选", "/capabilities/image", "image"));

        // 全局 Bean 设置分组（系统内部使用）
        result.add(globalBean("chat", "对话路由（ChatClient 会话）", "runtime",
                "全局 ChatClient 会话：供应商、API Key、默认模型、温度与超时", "/settings/global-beans", "runtime"));
        result.add(pipeline("rag", "RAG 检索增强", "rag", "rag",
                List.of("rag"), List.of("ocr", "rerank", "layout"),
                "文档入库+检索问答管线，入库 = 抽取→分块→向量化→入库，问答 = 检索→过滤→生成", "/capabilities/rag", "rag"));
        result.add(globalBean("input", "输入优化", "input",
                "全局对话输入自动优化（压缩/改写）", "/settings/global-beans", "input"));

        // 音频两条管线。
        //
        // 原先它们是 globalBean（只有客户端配置、没有模型槽），能力页打开后
        // 既没有标题、也看不到「这个管线需要哪些模型」。改造成 pipeline 后：
        //   语音识别 = ASR 模型（必填）+ 文本矫正模型（可选，参照 OCR 的 corrector_model）
        //   语音合成 = TTS 模型（必填）+ 文本规范化模型（可选，与矫正侧对称）
        // 两个可选模型都是「留空关闭、失败保留原文」，非必填。
        result.add(pipeline("asr", "语音识别", "asr", "asr",
                List.of("asr"), List.of("corrector"),
                "语音转文字管线：ASR 模型必填；文本矫正模型可选（非必填，留空关闭）——"
                        + "用大模型修正转写里的同音字、术语与标点", "/capabilities/asr", "asr"));
        result.add(pipeline("tts", "语音合成", "tts", "tts",
                List.of("tts"), List.of("text-normalize"),
                "文字转语音管线：TTS 模型必填；文本规范化模型可选（非必填，留空关闭）——"
                        + "合成前用大模型把数字、符号、缩写改写成适合朗读的表述", "/capabilities/tts", "tts"));

        result.add(globalBean("bot", "Bot 全局设置", "bot",
                "Bot 绑定默认行为与回复策略", "/settings/global-beans", "bot"));

        return result;
    }

    /**
     * 构造管线定义。
     */
    private static Map<String, Object> pipeline(String code, String name, String group, String tag,
                                               List<String> required, List<String> optional,
                                               String description, String route, String configGroup) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", name);
        item.put("group", group);
        item.put("tag", tag);
        item.put("required", required);
        item.put("optional", optional);
        item.put("description", description);
        item.put("route", route);
        item.put("configGroup", configGroup);
        item.put("kind", "pipeline");
        return item;
    }

    /**
     * 构造全局 Bean 设置分组定义。
     */
    private static Map<String, Object> globalBean(String code, String name, String group,
                                                  String description, String route, String configGroup) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", name);
        item.put("group", group);
        item.put("tag", group);
        item.put("required", List.of());
        item.put("optional", List.of());
        item.put("description", description);
        item.put("route", route);
        item.put("configGroup", configGroup);
        item.put("kind", "globalBean");
        return item;
    }
}
