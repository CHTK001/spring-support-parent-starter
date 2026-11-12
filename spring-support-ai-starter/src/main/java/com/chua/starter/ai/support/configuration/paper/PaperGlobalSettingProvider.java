// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * <p><b>不再提供 {@code engine} 配置项</b>：引擎由模型配置自动推导 —— 配了
 * {@code llm_model} 即为 {@code ocr+llm}，否则为 {@code ocr}（见
 * {@code PaperClientManager#engine()}）。界面少一个「识别引擎」下拉，
 * 用户无需理解两条管线的细节，配了 LLM 就自动结构化。</p>
 *
 * <p><b>不再提供 {@code llm_provider} 配置项</b>：结构化大模型必须来自
 * 「模型管理」中登记的模型，不允许在试卷配置里私自填写供应商。用户只需在
 * {@code llm_model} 里从模型管理选择对话模型即可，供应商由所选模型推导。</p>
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
                // 引擎不再提供配置项：由 PaperClientManager.engine() 按模型配置推导
                // （配了 llm_model → ocr+llm，否则 ocr）。移除后设置页
                // 少一个「识别引擎」下拉，用户不用理解双管线细节。
                // 版面模型：留空时回落到 layout 分组（装配见 PaperClientManager#resolveLayoutDetector）
                new GlobalSettingProvider.SettingItem("layout_model", "", "STRING", "版面分析模型（留空复用 layout 分组；阈值与设备请到「版面分析」分组设置）", 2),
                // OCR 模型：留空时回落到 ocr 分组（装配见 PaperClientManager#resolveOcrPipeline）
                new GlobalSettingProvider.SettingItem("ocr_detector_model", "", "STRING", "文字检测模型（留空复用 ocr 分组；置信度阈值与设备请到「OCR」分组设置）", 4),
                new GlobalSettingProvider.SettingItem("ocr_recognizer_model", "", "STRING", "文字识别模型（留空复用 ocr 分组）", 5),
                // LLM 结构化（engine=ocr+llm 时生效）
                // 结构化大模型只认模型管理的模型：不再提供 llm_provider（私自配供应商），
                // 供应商由所选模型自动推导（见 PaperClientManager.resolveLlmClient）。
                new GlobalSettingProvider.SettingItem("llm_model", "", "STRING", "结构化大模型（模型管理选择，留空用默认模型）", 8),
                // 2026-11-10：原 llm_prompt（结构化提示词）删除 —— 全仓无消费方读取，
                // 试卷结构化实际用的是内置提示词。
                // 2026-11-12：layout_threshold / ocr_threshold / device / max_pages 删除。
                // 前三项是模型级参数（ModelParamBridge 进程内按模型名共享），在 paper 组
                // 再声明一份只会与 layout/ocr 分组互相覆盖，真源应留在各自分组；
                // max_pages 则完全没有实现——PaperClient 的识别入口只接收单张图片字节，
                // 全仓没有任何分页/多页循环，填多少都不会改变行为。
                // 行为开关
                new GlobalSettingProvider.SettingItem("merge_answer", "true", "BOOLEAN", "是否合并题干与作答区域（false 仅文字块）", 11)
        );
    }}