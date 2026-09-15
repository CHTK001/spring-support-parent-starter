package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
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
* @author CH
* @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/ai/pipelines")
@Tag(name = "AI 管线目录")
public class AiPipelineController {

    /**
    * 管线目录。
    *
    * @return 管线定义列表
     */
    @Permission("admin")
    @Operation(summary = "AI 管线目录", description = "管线必填/非必填能力定义，驱动左侧菜单、能力页与模型管理")
    @GetMapping
    public ReturnResult<List<Map<String, Object>>> pipelines() {
        List<Map<String, Object>> result = new ArrayList<>();

        result.add(pipeline("text-to-image", "文生图", "generate", "text-to-image",
                List.of("text-to-image"), List.of("image-generate"),
                "基于提示词生成图像，全局 ImageClient 会话由 generate 分组驱动", "/capabilities/text-to-image", "generate"));
        result.add(pipeline("text-to-video", "文生视频", "generate", "text-to-video",
                List.of("text-to-video"), List.of(),
                "基于提示词生成视频，全局 VideoClient 会话由 generate 分组驱动", "/capabilities/text-to-video", "generate"));
        result.add(pipeline("ocr", "OCR 文字识别", "ocr", "ocr",
                List.of("ocr"), List.of("layout"),
                "文字检测→矫正→识别管线，检测/识别模型必填，方向与超分模型可选", "/capabilities/ocr", "ocr"));
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
        result.add(globalBean("asr", "语音识别", "asr",
                "全局语音识别 ASR 客户端配置", "/settings/global-beans", "asr"));
        result.add(globalBean("bot", "Bot 全局设置", "bot",
                "Bot 绑定默认行为与回复策略", "/settings/global-beans", "bot"));

        return ReturnResult.ok(result);
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
