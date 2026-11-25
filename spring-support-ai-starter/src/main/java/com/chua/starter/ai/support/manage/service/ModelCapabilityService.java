// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.account.channel.service.AccountChannelModelService;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.ai.support.manage.mapper.SysAiModelMetricsMapper;
import com.chua.starter.ai.support.manage.store.SysAiModelMetrics;
import com.chua.starter.ai.support.manage.store.SysAiModelPrice;
import com.chua.starter.ai.support.util.AiModelMetricKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型能力评估服务：固定六维雷达图与能力 TOP 榜。
 *
 * <p>六个维度固定（前端雷达图轴序与此一致）：</p>
 * <ol>
 *   <li><b>context</b> 上下文窗口 —— 对数归一（窗口跨度跨 3 个数量级）；</li>
 *   <li><b>reasoning</b> 推理 —— GPQA → AIME25 → LCR 依次回退，缺省智能指数；</li>
 *   <li><b>tool_calling</b> 工具调用 —— Tau2 分数，缺省按函数调用能力位给 0/100；</li>
 *   <li><b>multimodal</b> 多模态 —— MMMU-Pro 分数，缺省按图片输入能力位给 0/100；</li>
 *   <li><b>speed</b> 输出速度 —— 输出速率（tok/s），线性归一到全表最大值；</li>
 *   <li><b>value</b> 性价比 —— 智能指数 ÷ 混合单价（输入/输出均价，缺省单任务成本）。</li>
 * </ol>
 *
 * <p>归一化统计（最大值等）取全表真实数据，不做任何插值或 mock；
 * 无数据的维度记 0 分，综合分为六维等权均值。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCapabilityService {

    /**
     * 维度标识：上下文窗口
     */
    public static final String DIM_CONTEXT = "context";
    /**
     * 维度标识：推理
     */
    public static final String DIM_REASONING = "reasoning";
    /**
     * 维度标识：工具调用
     */
    public static final String DIM_TOOL_CALLING = "tool_calling";
    /**
     * 维度标识：多模态
     */
    public static final String DIM_MULTIMODAL = "multimodal";
    /**
     * 维度标识：输出速度
     */
    public static final String DIM_SPEED = "speed";
    /**
     * 维度标识：性价比
     */
    public static final String DIM_VALUE = "value";

    /**
     * 榜单默认条数
     */
    public static final int DEFAULT_RANKING_LIMIT = 20;

    /**
     * 指标库 Mapper
     */
    private final SysAiModelMetricsMapper metricsMapper;

    /**
     * 模型价格服务（价格拆分后，价格字段权威来源为 {@code sys_ai_model_price}）
     */
    private final SysAiModelPriceService modelPriceService;

    /**
     * 渠道模型业务（账号模块）：已添加（启用）模型清单
     */
    private final AccountChannelModelService channelModelService;

    /**
     * 单个维度的评估值。
     *
     * @param key   维度标识
     * @param label 维度名（中文）
     * @param value 原始值（无数据为 {@code null}）
     * @param score 归一化得分 0-100（无数据为 0）
     */
    public record CapabilityDimension(String key, String label, BigDecimal value, BigDecimal score) {
    }

    /**
     * 模型雷达图评估结果。
     *
     * @param modelKey  归一化模型键
     * @param modelName 模型名
     * @param dimensions 六维评估（轴序固定）
     * @param composite 综合分（六维等权均值，0-100）
     */
    public record CapabilityRadar(String modelKey, String modelName,
                                  List<CapabilityDimension> dimensions, BigDecimal composite) {
    }

    /**
     * 能力榜单行。
     *
     * @param modelKey   归一化模型键
     * @param modelName  模型名
     * @param providerId 渠道 id（首条启用行）
     * @param composite  综合分（0-100）
     * @param scores     维度得分（key → 0-100）
     */
    public record CapabilityRankingRow(String modelKey, String modelName, String providerId,
                                       BigDecimal composite, Map<String, BigDecimal> scores) {
    }

    /**
     * 单模型六维雷达图。
     *
     * @param modelKey 归一化模型键
     * @return 雷达评估；指标库无该模型返回 {@code null}
     */
    public CapabilityRadar radar(String modelKey) {
        String key = AiModelMetricKeys.normalizeKey(modelKey);
        if (key == null || key.isBlank()) {
            return null;
        }
        SysAiModelMetrics row = metricsMapper.selectOne(new LambdaQueryWrapper<SysAiModelMetrics>()
                .eq(SysAiModelMetrics::getModelKey, key).last("limit 1"));
        if (row == null) {
            return null;
        }
        List<SysAiModelMetrics> all = metricsMapper.selectList(null);
        Stats stats = Stats.of(all);
        List<CapabilityDimension> dimensions = dimensionsOf(row, stats);
        return new CapabilityRadar(row.getModelKey(), row.getModelName(),
                dimensions, composite(dimensions));
    }

    /**
     * 能力 TOP 榜：已添加（启用）模型按综合分排序。
     *
     * <p>数据源为渠道模型启用行（{@code listAllEnabled}，ai→account 契约），
     * 按归一化模型键匹配指标行；无指标数据的模型不参与排名（不伪造分数）。</p>
     *
     * @param limit 返回条数（&le;0 用默认值）
     * @return 榜单（综合分降序）
     */
    public List<CapabilityRankingRow> ranking(int limit) {
        int top = limit <= 0 ? DEFAULT_RANKING_LIMIT : Math.min(limit, 100);
        List<SysAiModelMetrics> all = metricsMapper.selectList(null);
        Stats stats = Stats.of(all);
        Map<String, SysAiModelMetrics> byKey = new HashMap<>(all.size());
        for (SysAiModelMetrics row : all) {
            if (row.getModelKey() != null) {
                byKey.putIfAbsent(row.getModelKey(), row);
            }
        }
        // 已添加模型按归一化键去重；首条启用行提供渠道归属与展示名
        Map<String, SysAccountChannelModel> added = new LinkedHashMap<>();
        for (SysAccountChannelModel modelRow : channelModelService.listAllEnabled()) {
            String key = AiModelMetricKeys.normalizeKey(modelRow.getModel());
            if (key == null || key.isBlank()) {
                continue;
            }
            added.putIfAbsent(key, modelRow);
        }
        List<CapabilityRankingRow> rows = new ArrayList<>(added.size());
        for (Map.Entry<String, SysAccountChannelModel> entry : added.entrySet()) {
            SysAiModelMetrics metrics = byKey.get(entry.getKey());
            if (metrics == null) {
                // 指标库未收录：不伪造分数，直接不进榜
                continue;
            }
            List<CapabilityDimension> dimensions = dimensionsOf(metrics, stats);
            rows.add(new CapabilityRankingRow(metrics.getModelKey(),
                    firstNonBlank(metrics.getModelName(), entry.getValue().getModel()),
                    entry.getValue().getProviderId(),
                    composite(dimensions), scoreMap(dimensions)));
        }
        rows.sort((a, b) -> b.composite().compareTo(a.composite()));
        return rows.size() > top ? new ArrayList<>(rows.subList(0, top)) : rows;
    }

    /**
     * 评估单行的六个固定维度。
     *
     * @param row   指标行
     * @param stats 全表统计
     * @return 六维评估（轴序固定）
     */
    private List<CapabilityDimension> dimensionsOf(SysAiModelMetrics row, Stats stats) {
        List<CapabilityDimension> list = new ArrayList<>(6);
        list.add(contextDimension(row, stats));
        list.add(reasoningDimension(row));
        list.add(toolCallingDimension(row));
        list.add(multimodalDimension(row));
        list.add(speedDimension(row, stats));
        list.add(valueDimension(row, stats));
        return list;
    }

    /**
     * 上下文窗口：对数归一到全表最大值。
     *
     * @param row   指标行
     * @param stats 全表统计
     * @return 维度评估
     */
    private CapabilityDimension contextDimension(SysAiModelMetrics row, Stats stats) {
        Long tokens = row.getContextWindowTokens();
        BigDecimal value = tokens == null ? null : BigDecimal.valueOf(tokens);
        BigDecimal score = BigDecimal.ZERO;
        if (value != null && stats.maxContext != null && stats.maxContext.signum() > 0) {
            double normalized = Math.log(1 + tokens) / Math.log(1 + stats.maxContext.doubleValue());
            score = clamp100(normalized * 100);
        }
        return new CapabilityDimension(DIM_CONTEXT, "上下文窗口", value, score);
    }

    /**
     * 推理：GPQA → AIME25 → LCR 依次回退，缺省智能指数（本身 0-100 量纲）。
     *
     * @param row 指标行
     * @return 维度评估
     */
    private CapabilityDimension reasoningDimension(SysAiModelMetrics row) {
        BigDecimal value = firstNonNull(row.getGpqaScore(), row.getAime25Score(),
                row.getLcrScore(), row.getIntelligenceIndex());
        return new CapabilityDimension(DIM_REASONING, "推理", value, clamp100(value));
    }

    /**
     * 工具调用：Tau2 分数，缺省按函数调用能力位给 0/100。
     *
     * @param row 指标行
     * @return 维度评估
     */
    private CapabilityDimension toolCallingDimension(SysAiModelMetrics row) {
        BigDecimal value = row.getTau2Score();
        BigDecimal score;
        if (value != null) {
            score = clamp100(value);
        } else {
            score = Boolean.TRUE.equals(row.getFunctionCalling()) ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return new CapabilityDimension(DIM_TOOL_CALLING, "工具调用", value, score);
    }

    /**
     * 多模态：MMMU-Pro 分数，缺省按图片输入能力位给 0/100。
     *
     * @param row 指标行
     * @return 维度评估
     */
    private CapabilityDimension multimodalDimension(SysAiModelMetrics row) {
        BigDecimal value = row.getMmmuProScore();
        BigDecimal score;
        if (value != null) {
            score = clamp100(value);
        } else {
            score = Boolean.TRUE.equals(row.getImageInput()) ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return new CapabilityDimension(DIM_MULTIMODAL, "多模态", value, score);
    }

    /**
     * 输出速度：输出速率（tok/s），优先本地实测均值，线性归一到全表最大值。
     *
     * @param row   指标行
     * @param stats 全表统计
     * @return 维度评估
     */
    private CapabilityDimension speedDimension(SysAiModelMetrics row, Stats stats) {
        BigDecimal value = row.getOutputSpeedTps() != null
                ? row.getOutputSpeedTps() : row.getAvgOutputSpeedTps();
        BigDecimal score = BigDecimal.ZERO;
        if (value != null && stats.maxSpeed != null && stats.maxSpeed.signum() > 0) {
            score = clamp100(value.doubleValue() / stats.maxSpeed.doubleValue() * 100);
        }
        return new CapabilityDimension(DIM_SPEED, "输出速度", value, score);
    }

    /**
     * 性价比：智能指数 ÷ 混合单价（输入/输出均价，缺省单任务成本），线性归一到全表最大值。
     *
     * @param row   指标行
     * @param stats 全表统计
     * @return 维度评估
     */
    private CapabilityDimension valueDimension(SysAiModelMetrics row, Stats stats) {
        BigDecimal intelligence = row.getIntelligenceIndex();
        BigDecimal blended = blendedPrice(row);
        BigDecimal value = null;
        if (intelligence != null && blended != null && blended.signum() > 0) {
            value = intelligence.divide(blended, 4, RoundingMode.HALF_UP);
        }
        BigDecimal score = BigDecimal.ZERO;
        if (value != null && stats.maxValue != null && stats.maxValue.signum() > 0) {
            score = clamp100(value.doubleValue() / stats.maxValue.doubleValue() * 100);
        }
        return new CapabilityDimension(DIM_VALUE, "性价比", value, score);
    }

    /**
     * 混合单价：输入/输出均价；两者皆缺时回落单任务成本。
     *
     * @param row 指标行
     * @return 混合单价；无价格数据返回 {@code null}
     */
    private BigDecimal blendedPrice(SysAiModelMetrics row) {
        // 价格拆分后以价格库为权威，指标库旧列仅作历史数据回退
        SysAiModelPrice price = row.getModelKey() == null
                ? null : modelPriceService.findByKey(row.getModelKey());
        BigDecimal input = price != null && price.getPriceInput() != null
                ? price.getPriceInput() : row.getPriceInputPerMusd();
        BigDecimal output = price != null && price.getPriceOutput() != null
                ? price.getPriceOutput() : row.getPriceOutputPerMusd();
        if (input != null && output != null) {
            return input.add(output).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        }
        if (input != null) {
            return input;
        }
        if (output != null) {
            return output;
        }
        if (price != null && price.getCostPerTask() != null) {
            return price.getCostPerTask();
        }
        return row.getCostPerTask();
    }

    /**
     * 综合分：六维等权均值（无数据维度按 0 计）。
     *
     * @param dimensions 六维评估
     * @return 综合分 0-100
     */
    private BigDecimal composite(List<CapabilityDimension> dimensions) {
        double sum = 0;
        for (CapabilityDimension dimension : dimensions) {
            sum += dimension.score() == null ? 0 : dimension.score().doubleValue();
        }
        return BigDecimal.valueOf(sum / dimensions.size()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 维度得分 → key/score 映射（榜单行用）。
     *
     * @param dimensions 六维评估
     * @return 维度得分映射
     */
    private Map<String, BigDecimal> scoreMap(List<CapabilityDimension> dimensions) {
        Map<String, BigDecimal> map = new LinkedHashMap<>(dimensions.size());
        for (CapabilityDimension dimension : dimensions) {
            map.put(dimension.key(), dimension.score());
        }
        return map;
    }

    /**
     * 归一化统计（全表）。
     *
     * @param maxContext 最大上下文窗口
     * @param maxSpeed   最大输出速度
     * @param maxValue   最大性价比比值
     */
    private record Stats(BigDecimal maxContext, BigDecimal maxSpeed, BigDecimal maxValue) {

        /**
         * 从全表指标行统计归一化基准。
         *
         * @param rows 全表指标行
         * @return 统计
         */
        static Stats of(List<SysAiModelMetrics> rows) {
            BigDecimal maxContext = null;
            BigDecimal maxSpeed = null;
            BigDecimal maxValue = null;
            for (SysAiModelMetrics row : rows) {
                if (row.getContextWindowTokens() != null) {
                    BigDecimal v = BigDecimal.valueOf(row.getContextWindowTokens());
                    maxContext = max(maxContext, v);
                }
                BigDecimal speed = row.getOutputSpeedTps() != null
                        ? row.getOutputSpeedTps() : row.getAvgOutputSpeedTps();
                if (speed != null) {
                    maxSpeed = max(maxSpeed, speed);
                }
                BigDecimal intelligence = row.getIntelligenceIndex();
                BigDecimal blended = blendedPriceOf(row);
                if (intelligence != null && blended != null && blended.signum() > 0) {
                    maxValue = max(maxValue, intelligence.divide(blended, 4, RoundingMode.HALF_UP));
                }
            }
            return new Stats(maxContext, maxSpeed, maxValue);
        }

        /**
         * 混合单价（与 {@link #blendedPrice(SysAiModelMetrics)} 同口径的静态版）。
         *
         * @param row 指标行
         * @return 混合单价
         */
        private static BigDecimal blendedPriceOf(SysAiModelMetrics row) {
            BigDecimal input = row.getPriceInputPerMusd();
            BigDecimal output = row.getPriceOutputPerMusd();
            if (input != null && output != null) {
                return input.add(output).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
            }
            if (input != null) {
                return input;
            }
            if (output != null) {
                return output;
            }
            return row.getCostPerTask();
        }

        /**
         * 取较大值。
         *
         * @param a 值一（可为 {@code null}）
         * @param b 值二
         * @return 较大值
         */
        private static BigDecimal max(BigDecimal a, BigDecimal b) {
            return a == null || b.compareTo(a) > 0 ? b : a;
        }
    }

    /**
     * 钳制到 0-100。
     *
     * @param value 原值
     * @return 0-100 得分
     */
    private static BigDecimal clamp100(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        double v = value.doubleValue();
        if (v < 0) {
            v = 0;
        }
        if (v > 100) {
            v = 100;
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 钳制到 0-100。
     *
     * @param value 原值
     * @return 0-100 得分
     */
    private static BigDecimal clamp100(double value) {
        return clamp100(BigDecimal.valueOf(value));
    }

    /**
     * 取第一个非空值。
     *
     * @param values 候选值
     * @return 第一个非空值；全空返回 {@code null}
     */
    private static BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * 取第一个非空字符串。
     *
     * @param a 候选一
     * @param b 候选二
     * @return 非空者
     */
    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
