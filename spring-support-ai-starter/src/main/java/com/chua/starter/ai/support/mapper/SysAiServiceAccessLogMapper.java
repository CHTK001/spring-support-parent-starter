package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiServiceAccessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 服务访问日志数据访问接口。
 *
 * <p>聚合语句统一写在 {@code SysAiServiceAccessLogMapper.xml}，本接口只声明方法：
 * 本仓禁止注解式 SQL（见 {@code SysAiKnowledgeBaseMapper} 的约定）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiServiceAccessLogMapper extends BaseMapper<SysAiServiceAccessLog> {

    /**
     * 入站流量按 AppKey 聚合（最近窗口内）。
     *
     * @param since 起始时间（含）
     * @param limit 最多返回的 AppKey 数
     * @return 按调用次数降序的聚合行
     */
    List<AppKeyTrafficRow> selectAppKeyTraffic(@Param("since") LocalDateTime since,
                                               @Param("limit") int limit);

    /**
     * 入站流量总计（最近窗口内）。
     *
     * @param since 起始时间（含）
     * @return 总计行；窗口内无请求时各字段为 0
     */
    TrafficTotalRow selectTrafficTotal(@Param("since") LocalDateTime since);

    /**
     * 单个 AppKey 的入站聚合。
     *
     * @param appKey AppKey 值
     * @param calls  调用次数
     * @param successCalls 成功次数（2xx/3xx）
     * @param tokens 令牌合计
     * @param avgLatencyMs 平均耗时（毫秒）
     * @param lastAt 最近一次请求时间
     */
    record AppKeyTrafficRow(String appKey, Long calls, Long successCalls, Long tokens,
                            Double avgLatencyMs, LocalDateTime lastAt) {
    }

    /**
     * 入站流量总计。
     *
     * @param calls  调用次数
     * @param successCalls 成功次数（2xx/3xx）
     * @param tokens 令牌合计
     * @param avgLatencyMs 平均耗时（毫秒）
     * @param lastAt 最近一次请求时间
     */
    record TrafficTotalRow(Long calls, Long successCalls, Long tokens,
                           Double avgLatencyMs, LocalDateTime lastAt) {
    }
}
