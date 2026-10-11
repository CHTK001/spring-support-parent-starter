package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.AiUsage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 用量服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiUsageService {

    /**
     * 无主用量的兜底归属账号。
     *
     * <p>外部工具用量（opencode / claude-code / cline 等）由定时任务同步进来，
     * 同步跑在调度线程上、拿不到登录态。历史上这里直接写 {@code null}，导致
     * 全量约三分之一的 Token 对**所有**账号不可见（{@code user_id = 'x'} 匹配不到 NULL），
     * 等于这部分账单凭空消失。</p>
     *
     * <p>这类数据本质是机器级而非账号级的，所以统一挂到 {@code admin} 名下，
     * 由管理员账号查看 —— 既不凭空丢失，也不破坏账号隔离（普通账号依旧只看自己）。</p>
     */
    String ORPHAN_OWNER = "admin";

    /**
     * 解析一条用量记录的有效归属账号。
     *
     * <p><b>所有写入路径都必须过这里</b>，否则 NULL 又会从某个漏网的调用点冒出来。
     * 历史上就是因为同步任务直接调 {@code AiUserContext.currentUserId()}，
     * 调度线程上取到 null，绕过了归属校验。</p>
     *
     * @param userId 原始归属（可为空）
     * @return 归属账号；为空时返回 {@link #ORPHAN_OWNER}
     */
    static String resolveOwner(String userId) {
        if (userId == null) {
            return ORPHAN_OWNER;
        }
        String trimmed = userId.trim();
        return trimmed.isEmpty() ? ORPHAN_OWNER : trimmed;
    }

    /**
     * 记录一次调用用量
     *
     * @param groupName 分组
     * @param provider 服务商
     * @param model 模型
     * @param callType 调用类型
     * @param usage 用量信息
     * @param requestId 请求标识
     * @param userId 用户标识
     */
    void record(String groupName, String provider, String model, String callType,
                AiUsage usage, String requestId, String userId);

    /**
     * 记录一次调用用量（带来源标记）
     * @param source 用量来源: service/third_party/self
     */
    void record(String groupName, String provider, String model, String callType,
                AiUsage usage, String requestId, String userId, String source);

    /**
     * 查询用量列表
     *
     * @param groupName 分组
     * @param provider 服务商
     * @param model 模型
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param userId 归属用户（可为空，为空时列出全部用户明细）
     * @param page 页码
     * @param size 每页大小
     * @return 用量列表
     */
    Page<SysAiUsage> list(String groupName, String provider, String model,
                          LocalDateTime startTime, LocalDateTime endTime, String userId, int page, int size);

    /**
     * 查询总用量统计
     *
     * @param groupName 分组
     * @param provider 服务商
     * @param model 模型
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param userId 归属用户（可为空，为空时统计全部用户）
     * @return 统计结果
     */
    UsageStatistics getStatistics(String groupName, String provider, String model,
                                  LocalDateTime startTime, LocalDateTime endTime, String userId);

    /**
     * 查询按天趋势（令牌 使用趋势）。
     *
     * <p>统计全部用户时直接读日汇总表 {@code sys_ai_usage_daily}；日汇总表不带用户维度，
     * 因此指定 {@code userId} 时改为对明细表按天聚合，保证普通用户只看得到自己的趋势。</p>
     *
     * @param provider 服务商（可为空）
     * @param model    模型（可为空）
     * @param startTime 开始时间（可为空）
     * @param endTime   结束时间（可为空）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 按天排序的趋势点列表（statDate 升序）
     */
    List<SysAiUsageDaily> getDailyTrend(String provider, String model,
                                        LocalDateTime startTime, LocalDateTime endTime, String userId);

    /**
     * 单日按小时聚合的趋势（趋势图今天/同一天时用）。
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param userId    归属用户（可为空）
     * @return 按小时升序的趋势点
     */
    List<com.chua.starter.ai.support.model.vo.AiUsageTrendPointVO> getHourlyTrend(
            LocalDateTime startTime, LocalDateTime endTime, String userId);

    /**
     * 「按天 × 按模型」聚合趋势（每日模型堆叠柱状图的数据源）。
     *
     * <p>与 {@link #getDailyTrend} 的区别：后者把同一天的所有模型合并成一行，
     * 拿不到「某天各模型分别多少」；本方法保留模型维度，一天内的多行由前端
     * 归并成同一根柱内的一段。始终走明细表的数据库端聚合而不是日汇总表，
     * 因为日汇总表的写入有定时延迟，用它会让「今天」的柱子先空一拍。</p>
     *
     * @param startTime 开始时间（含）
     * @param endTime   结束时间（不含）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 按天升序、同天内按令牌降序的聚合点列表
     */
    List<com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO> getDailyModelTrend(
            LocalDateTime startTime, LocalDateTime endTime, String userId);

    /**
     * 用量统计摘要
     * @author CH
     * @since 4.0.0
     */
    class UsageStatistics {
        /**
         * 总数calls
        */
        public Long totalCalls;
        /**
         * 总数prompttokens
        */
        public Long totalPromptTokens;
        /**
         * 总数completiontokens
        */
        public Long totalCompletionTokens;
        /**
         * 总数令牌
        */
        public Long totalTokens;
        /**
         * 总数cost
        */
        public Double totalCost;
        /**
         * 缓存读取令牌
        */
        public Long totalCachedTokens;
        /**
         * 原始费用(未折扣)
        */
        public Double standardCost;
        /**
         * 平均耗时(ms)
        */
        public Double avgLatency;
        /**
         * 平均首字延迟(ms)
        */
        public Double avgFirstTokenLatency;
    }
}
