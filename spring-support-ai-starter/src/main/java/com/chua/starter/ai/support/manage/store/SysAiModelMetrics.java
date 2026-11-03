package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 全量模型指标库（数据源：Artificial Analysis）。
 *
 * <p>承载指标源能采集到的全部模型指标，与本地「配置了哪些模型」无关；
 * 配置模型（sys_ai_provider_model）不持有指标，计费/展示按模型名（{@link #modelKey}）
 * 关联本表取价。被 {@link #manualLocked} 标记的字段在全量同步时不覆盖。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_model_metrics", autoResultMap = true)
public class SysAiModelMetrics implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 主键
     */
    @TableId(value = "sys_ai_model_metrics_id", type = IdType.AUTO)
    private Long id; // 标识

    /**
     * 归一化模型键（唯一，关联键）
     */
    @TableField("sys_ai_model_metrics_model_key")
    private String modelKey; // 模型键

    /**
     * 模型名称（展示用）
     */
    @TableField("sys_ai_model_metrics_model")
    private String modelName; // 模型名称

    /**
     * 厂商（可空）
     */
    @TableField("sys_ai_model_metrics_provider")
    private String provider; // 厂商

    /**
     * 输入单价（USD/百万 token）
     */
    @TableField("sys_ai_model_metrics_price_input")
    private BigDecimal priceInputPerMusd; // 输入价

    /**
     * 输出单价（USD/百万 token）
     */
    @TableField("sys_ai_model_metrics_price_output")
    private BigDecimal priceOutputPerMusd; // 输出价

    /**
     * 缓存命中输入价（USD/百万 token）
     */
    @TableField("sys_ai_model_metrics_price_cache_hit")
    private BigDecimal priceCacheHitPerMusd; // 缓存命中价

    /**
     * 缓存写入价（USD/百万 token）
     */
    @TableField("sys_ai_model_metrics_price_cache_write")
    private BigDecimal priceCacheWritePerMusd; // 缓存写入价

    /**
     * 智能指数
     */
    @TableField("sys_ai_model_metrics_intelligence_index")
    private BigDecimal intelligenceIndex; // 智能指数
    /**
     * HLE（Humanity's Last Exam，人类最后考试）得分（0~1）
     */
    @TableField("sys_ai_model_metrics_hle_score")
    private BigDecimal hleScore; // HLE得分

    /**
     * 全知指数（知识准确-幻觉，可为负）
     */
    @TableField("sys_ai_model_metrics_omniscience_index")
    private BigDecimal omniscienceIndex; // 全知指数

    /**
     * GPQA 得分（0~1）
     */
    @TableField("sys_ai_model_metrics_gpqa_score")
    private BigDecimal gpqaScore; // GPQA

    /**
     * AIME 2025 数学基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_aime25_score")
    private BigDecimal aime25Score; // AIME25

    /**
     * LiveCodeBench 编程基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_livecodebench_score")
    private BigDecimal livecodebenchScore; // LiveCodeBench

    /**
     * SciCode 科学编程基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_scicode_score")
    private BigDecimal scicodeScore; // SciCode

    /**
     * IFBench 指令遵循基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_ifbench_score")
    private BigDecimal ifbenchScore; // IFBench

    /**
     * CritPt 物理基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_critpt_score")
    private BigDecimal critptScore; // CritPt

    /**
     * MMMU-Pro 多模态基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_mmmu_pro_score")
    private BigDecimal mmmuProScore; // MMMU-Pro

    /**
     * Tau2-Bench 工具调用基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_tau2_score")
    private BigDecimal tau2Score; // Tau2-Bench

    /**
     * Tau-Banking 银行场景基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_tau_banking_score")
    private BigDecimal tauBankingScore; // Tau-Banking

    /**
     * LCR 长上下文推理得分（0~1）
     */
    @TableField("sys_ai_model_metrics_lcr_score")
    private BigDecimal lcrScore; // LCR

    /**
     * Terminal-Bench Hard 得分（0~1）
     */
    @TableField("sys_ai_model_metrics_terminalbench_hard_score")
    private BigDecimal terminalbenchHardScore; // TerminalBench-Hard

    /**
     * Terminal-Bench 2.1 得分（0~1）
     */
    @TableField("sys_ai_model_metrics_terminal_bench21_score")
    private BigDecimal terminalBench21Score; // TerminalBench-2.1

    /**
     * Terminal-Bench 4.0 得分（0~1）
     */
    @TableField("sys_ai_model_metrics_terminal_bench40_score")
    private BigDecimal terminalBench40Score; // TerminalBench-4.0

    /**
     * AutomationBench 自动化基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_automation_bench_score")
    private BigDecimal automationBenchScore; // AutomationBench

    /**
     * GDPval 归一化得分（0~1）
     */
    @TableField("sys_ai_model_metrics_gdpval_normalized_score")
    private BigDecimal gdpvalNormalizedScore; // GDPval

    /**
     * Briefcase 专业任务基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_briefcase_score")
    private BigDecimal briefcaseScore; // Briefcase

    /**
     * Harvey Lab 法律任务基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_harvey_lab_score")
    private BigDecimal harveyLabScore; // HarveyLab

    /**
     * Apex Agents 基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_apex_agents_score")
    private BigDecimal apexAgentsScore; // ApexAgents

    /**
     * ITBench SRE 运维基准得分（0~1）
     */
    @TableField("sys_ai_model_metrics_itbench_sre_score")
    private BigDecimal itbenchSreScore; // ITBench-SRE

    /**
     * 全知指数-准确率（0~1）
     */
    @TableField("sys_ai_model_metrics_omniscience_accuracy")
    private BigDecimal omniscienceAccuracy; // 全知准确率

    /**
     * 全知指数-非幻觉率（0~1）
     */
    @TableField("sys_ai_model_metrics_omniscience_non_hallucination")
    private BigDecimal omniscienceNonHallucination; // 全知非幻觉

    /**
     * Artificial Analysis Elo 评分
     */
    @TableField("sys_ai_model_metrics_elo")
    private BigDecimal elo; // Elo

    /**
     * 单任务平均成本（USD）
     */
    @TableField("sys_ai_model_metrics_cost_per_task")
    private BigDecimal costPerTask; // 任务成本(USD)

    /**
     * 是否支持 JSON 输出模式
     */
    @TableField("sys_ai_model_metrics_json_mode")
    private Boolean jsonMode; // JSON模式

    /**
     * 是否兼容 OpenAI 协议
     */
    @TableField("sys_ai_model_metrics_openai_compatible")
    private Boolean openaiCompatible; // OpenAI兼容

    /**
     * 智能指数是否为估算值
     */
    @TableField("sys_ai_model_metrics_intelligence_index_estimated")
    private Boolean intelligenceIndexEstimated; // 智能指数估算

    /**
     * 输出速度（token/秒）
     */
    @TableField("sys_ai_model_metrics_output_speed_tps")
    private BigDecimal outputSpeedTps; // 输出速度

    /**
     * 本地平均输出速度（token/秒）
     *
     * <p>由用量聚合任务按 provider+model 对全量本地用量的 {@code tokens_per_second}
     * 取平均计算得出，不来自 Artificial Analysis 指标源：
     * 不入手动锁定字段集、不入指标快照白名单，无本地用量的模型保持空（前端回退
     * 到 {@link #outputSpeedTps}）。</p>
     */
    @TableField("sys_ai_model_metrics_avg_output_speed_tps")
    private BigDecimal avgOutputSpeedTps; // 本地平均输出速度

    /**
     * 首 token 延迟（秒）
     */
    @TableField("sys_ai_model_metrics_latency_first_token")
    private BigDecimal latencyFirstTokenSeconds; // 首token延迟

    /**
     * 上下文窗口（token 数）
     */
    @TableField("sys_ai_model_metrics_context_window")
    private Long contextWindowTokens; // 上下文窗口

    /**
     * 图标地址
     */
    @TableField("sys_ai_model_metrics_icon_url")
    private String iconUrl; // 图标

    /**
     * 是否支持深度思考（推理）
     */
    @TableField("sys_ai_model_metrics_support_reasoning")
    private Boolean reasoning; // 深度思考

    /**
     * 是否支持联网检索
     */
    @TableField("sys_ai_model_metrics_support_web_search")
    private Boolean webSearch; // 联网检索

    /**
     * 是否支持图片输入（视觉）
     */
    @TableField("sys_ai_model_metrics_support_image_input")
    private Boolean imageInput; // 图片输入

    /**
     * 是否支持函数调用
     */
    @TableField("sys_ai_model_metrics_support_function_call")
    private Boolean functionCalling; // 函数调用

    /**
     * 手动锁定字段名集合（同步时这些字段不覆盖）
     */
    @TableField(value = "sys_ai_model_metrics_manual_locked", typeHandler = JacksonTypeHandler.class)
    private List<String> manualLocked; // 锁定字段

    /**
     * 手动修改时间（毫秒）
     */
    @TableField("sys_ai_model_metrics_manual_updated_at")
    private Long manualUpdatedAt; // 手动修改时间

    /**
     * 手动修改人
     */
    @TableField("sys_ai_model_metrics_manual_updated_by")
    private String manualUpdatedBy; // 手动修改人

    /**
     * 数据源标识
     */
    @TableField("sys_ai_model_metrics_source")
    private String source; // 数据源

    /**
     * 创建时间（毫秒）
     */
    @TableField("create_time")
    private Long createTime; // 创建时间

    /**
     * 更新时间（毫秒）
     */
    @TableField("update_time")
    private Long updateTime; // 更新时间
}
