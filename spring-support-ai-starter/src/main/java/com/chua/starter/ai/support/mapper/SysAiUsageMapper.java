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
     * @param includeOrphan 是否把无主数据（{@code user_id IS NULL}，定时同步的外部工具用量）
     *                       计入结果。仅管理员为 {@code true}；普通账号必须为 {@code false}，
     *                       否则会看到不属于自己机器的外部工具用量。
     * @return 聚合统计行，所有字段可能为 空（无数据时）
     */
    UsageStatsRow selectStatistics(
            @Param("groupName") String groupName,
            @Param("provider") String provider,
            @Param("model") String model,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("userId") String userId,
            @Param("includeOrphan") boolean includeOrphan
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
     * @param userId    归属账号。<b>日汇总表必须带这一维度</b>：不分组的话不同账号的用量
     *                   会被合并成一行，日后任何按账号过滤的读取都会把别人的量算进来
     */
    record DailyAggregateRow(
            LocalDate statDate,
            String groupName,
            String provider,
            String model,
            String userId,
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
            @Param("userId") String userId,
            @Param("includeOrphan") boolean includeOrphan
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
            @Param("userId") String userId,
            @Param("includeOrphan") boolean includeOrphan
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
            @Param("userId") String userId,
            @Param("includeOrphan") boolean includeOrphan
    );

    /**
     * 「按天 × 按模型」聚合行，供用量管理的每日堆叠柱状图使用。
     *
     * @param statDate  统计日期
     * @param provider  服务商
     * @param model     模型
     * @param totalCalls 当日该模型调用次数
     * @param totalTokens 当日该模型令牌合计
     * @param totalCost 当日该模型费用
     * @param avgFirstTokenLatency 当日该模型平均首字延迟（毫秒；无实测样本时为 null）
     * @param avgTotalLatency 当日该模型平均总耗时（毫秒；无实测样本时为 null）
     */
    record DailyModelRow(
            LocalDate statDate,
            String provider,
            String model,
            Long totalCalls,
            Long totalTokens,
            Double totalCost,
            Double avgFirstTokenLatency,
            Double avgTotalLatency
    ) {}

    /**
     * 数据库端按「天 + 服务商 + 模型」三维分组聚合。
     *
     * <p>与 {@link #selectDailyAggregate} 的区别只在于少了 groupName 一维：
     * 柱状图要的是「每天一根柱、柱内按模型分段」，分组维度越少，前端需要
     * 归并的同色段越少、图例越干净。</p>
     *
     * @param startTime 开始时间（含），不能为 空
     * @param endTime   结束时间（不含），不能为 空
     * @param userId    归属用户（可为空，为空时聚合全部用户）
     * @return 按天升序、同天内按令牌降序的聚合行列表
     */
    List<DailyModelRow> selectDailyModelAggregate(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("userId") String userId,
            @Param("includeOrphan") boolean includeOrphan
    );

    /**
     * 本地平均输出速度：按 服务商+模型 分组对全部本地用量的 token 输出速度取平均。
     *
     * <p>数据源是 {@code sys_ai_usage.tokens_per_second}（decode 窗口的 token/秒），
     * 覆盖所有从本地写下的调用（工具扫描/定时同步），是真正的「本机用过多少速度」；
     * 没有本地用量的模型不出现（空，前端回退到 Artificial Analysis 的输出速度）。</p>
     *
     * @param provider 服务商（可为空，为空时不限制）
     * @param model    模型（可为空，为空时不限制）
     * @return 按 provider+model 分组的平均速度行列表，无数据时为空列表
     */
    record ModelSpeedRow(
            String provider,
            String model,
            Double avgSpeedTps
    ) {}

    /**
     * 数据库端按「服务商 + 模型」分组，对全部本地用量的 token 输出速度取平均。
     *
     * @param provider 服务商（可为空，为空时不限制）
     * @param model    模型（可为空，为空时不限制）
     * @return 按 provider+model 分组的平均速度行列表，无数据时为空列表
     */
    List<ModelSpeedRow> selectModelAvgSpeedTps(
            @Param("provider") String provider,
            @Param("model") String model
    );
}
