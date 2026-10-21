package com.chua.starter.ai.support.configuration.paper;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 试卷识别模块全局设置提供者 SPI。
 *
 * <p>定义 {@code paper} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。</p>
 *
 * <p>试卷识别支持两种管线：</p>
 * <ul>
 *   <li><b>OCR</b>（{@code engine=ocr}）：版面分析 + 文字识别，只出文本块。</li>
 *   <li><b>OCR + LLM</b>（{@code engine=ocr+llm}）：OCR 出文本后，再调用大模型
 *       把整页文本结构化为「题号 / 题干 / 作答」，用于外来卷等没有题库的判分。</li>
 * </ul>
 *
 * <p>模型字段可复用 {@code ocr} / {@code layout} 分组，也可在本分组独立配置；
 * 留空时按复用优先（优先读本分组，其次读 ocr / layout 分组）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("paper")
public class PaperGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "paper";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "试卷识别";

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    public List<GlobalSettingProvider.SettingItem> getItems() {
        return List.of(
                // 引擎：ocr = 纯 OCR（版面+文字块）；ocr+llm = OCR 后接 LLM 结构化
                new GlobalSettingProvider.SettingItem("engine", "ocr", "STRING", "试卷识别引擎（ocr / ocr+llm）", 1),
                // 版面模型：留空时回落到 layout 分组
                new GlobalSettingProvider.SettingItem("layout_model", "", "STRING", "版面分析模型（留空复用 layout 分组）", 2),
                new GlobalSettingProvider.SettingItem("layout_threshold", "0.5", "NUMBER", "版面置信度阈值（0~1）", 3),
                // OCR 模型：留空时回落到 ocr 分组
                new GlobalSettingProvider.SettingItem("ocr_detector_model", "", "STRING", "文字检测模型（留空复用 ocr 分组）", 4),
                new GlobalSettingProvider.SettingItem("ocr_recognizer_model", "", "STRING", "文字识别模型（留空复用 ocr 分组）", 5),
                new GlobalSettingProvider.SettingItem("ocr_threshold", "0.5", "NUMBER", "OCR 检测置信度阈值（0~1）", 6),
                // LLM 结构化（engine=ocr+llm 时生效）
                new GlobalSettingProvider.SettingItem("llm_provider", "", "STRING", "结构化大模型提供者（留空用默认模型）", 7),
                new GlobalSettingProvider.SettingItem("llm_model", "", "STRING", "结构化大模型名称（留空用默认模型）", 8),
                new GlobalSettingProvider.SettingItem("llm_prompt", "", "STRING", "结构化提示词（留空用内置默认）", 9),
                // 行为开关
                new GlobalSettingProvider.SettingItem("max_pages", "20", "NUMBER", "单任务最大处理页数", 10),
                new GlobalSettingProvider.SettingItem("merge_answer", "true", "BOOLEAN", "是否合并题干与作答区域（false 仅文字块）", 11),
                new GlobalSettingProvider.SettingItem("device", "cpu", "STRING", "推理设备（cpu / cuda）", 12)
        );
    }
}