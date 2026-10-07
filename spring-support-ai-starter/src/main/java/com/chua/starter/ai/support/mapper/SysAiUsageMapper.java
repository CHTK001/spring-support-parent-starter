package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 用量 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiUsageMapper extends BaseMapper<SysAiUsage> {

    /**
     * 用量聚合统计结果。
     *
     * @param totalCalls          总调用次数
     * @param totalPromptTokens   总输入 令牌
     * @param totalCompletionTokens 总输出 令牌
     * @param totalTokens         总 令牌
     * @param totalCost           总费用（元）
     * @param totalCachedTokens   缓存读取 令牌
     * @param standardCost        标准费用（官方价格计算，未折扣）
     * @param totalLatency        总耗时（ms）
     * @param totalFirstTokenLatency 总首字延迟（ms）
     */
    record UsageStatsRow(
            Long totalCalls,
            Long totalPromptTokens,
            Long totalCompletionTokens,
            Long totalTokens,
            Double totalCost,
            Long totalCachedTokens,
            Double standardCost,
            Long totalLatency,
            Long totalFirstTokenLatency
    ) {}

    /**
     * 数据库端聚合统计——单条 SQL 完成 SUM/数量，配合索引秒级返回，避免全量装载到 JVM。
     *
     * @param groupName 分组名称（可为空，为空时不按分组过滤）
     * @param provider  服务商（可为空）
     * @param model     模型（可为空）
     * @param startTime 开始时间（可为空，不传则不限起始时间）
     * @param endTime   结束时间（可为空，不传则不限截止时间）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 聚合统计行，所有字段可能为 空（无数据时）
     */
    UsageStatsRow selectStatistics(
            @Param("groupName") String groupName,
            @Param("provider") String provider,
            @Param("model") String model,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("userId") String userId
    );

    /**
     * 按天分组的日聚合结果（供每日汇总表回填）。
     *
     * @param statDate 统计日期
     * @param groupName 分组名称
     * @param provider  服务商
     * @param model     模型
     * @param totalCalls 总调用次数
     * @param totalPromptTokens 总输入 令牌
     * @param totalCompletionTokens 总输出 令牌
     * @param totalTokens 总 令牌
     * @param totalCost 总费用（元）
     * @param totalCachedTokens 缓存读取 令牌
     * @param totalLatency 总耗时（ms）
     * @param totalFirstTokenLatency 总首字延迟（ms）
     */
    record DailyAggregateRow(
            LocalDate statDate,
            String groupName,
            String provider,
            String model,
            Long totalCalls,
            Long totalPromptTokens,
            Long totalCompletionTokens,
            Long totalTokens,
            Double totalCost,
            Long totalCachedTokens,
            Long totalLatency,
            Long totalFirstTokenLatency
    ) {}

    /**
     * 数据库端按天、群体、提供者、模型 分组聚合，一次 SQL 返回全部日汇总数据。
     *
     * @param startTime 开始时间（含），不能为 空
     * @param endTime   结束时间（不含），不能为 空
     * @param userId    归属用户（可为空，为空时聚合全部用户，日汇总回填即走该分支）
     * @return 按天分组的聚合行列表，无数据时为空列表
     */
    List<DailyAggregateRow> selectDailyAggregate(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("userId") String userId
    );

    /**
     * 按小时分组的聚合结果（单日趋势用）。
     *
     * @param statHour 小时标签，格式 yyyy-MM-dd HH:00
     * @param totalCalls 总调用次数
     * @param totalPromptTokens 总输入 令牌
     * @param totalCompletionTokens 总输出 令牌
     * @param totalTokens 总 令牌
     * @param totalCost 总费用（元）
     * @param totalCachedTokens 缓存读取 令牌
     */
    record HourlyAggregateRow(
            String statHour,
            Long totalCalls,
            Long totalPromptTokens,
            Long totalCompletionTokens,
            Long totalTokens,
            Double totalCost,
            Long totalCachedTokens
    ) {}

    /**
     * 数据库端按小时分组聚合（供单日趋势图使用）。
     *
     * @param startTime 开始时间（含）
     * @param endTime   结束时间（不含）
     * @param userId    归属用户（可为空）
     * @return 按小时分组的聚合行列表
     */
    List<HourlyAggregateRow> selectHourlyAggregate(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("userId") String userId
    );

    /**
     * 按分组、服务商、模型维度聚合的用量汇总行。
     *
     * @param groupName             分组名称
     * @param provider              服务商
     * @param model                 模型
     * @param totalCalls            调用次数
     * @param totalPromptTokens     输入 令牌
     * @param totalCompletionTokens 输出 令牌
     * @param totalTokens           总 令牌
     * @param totalCost             总费用（元）
     */
    record UsageSummaryRow(
            String groupName,
            String provider,
            String model,
            Long totalCalls,
            Long totalPromptTokens,
            Long totalCompletionTokens,
            Long totalTokens,
            Double totalCost
    ) {}

    /**
     * 数据库端按 分组+服务商+模型 聚合用量，一次 SQL 返回全部分组行。
     *
     * @param model     模型（可为空）
     * @param startTime 开始时间（可为空）
     * @param endTime   结束时间（可为空）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 维度聚合行列表，无数据时为空列表
     */
    List<UsageSummaryRow> selectUsageSummary(
            @Param("model") String model,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("userId") String userId
    );
}
