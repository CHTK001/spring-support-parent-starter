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
     * 入站流量按 AppKey 聚合（最近窗口内、指定路由组）。
     *
     * @param since        起始时间（含）
     * @param routerGroup  路由组供应商 ID
     * @param restrictOwner 是否按归属过滤（管理员为 {@code false}，可见全部）
     * @param ownerUserId  归属用户标识；{@code restrictOwner} 为真且该值为 {@code null} 时只看无主 Key
     * @param limit        最多返回的 AppKey 数
     * @return 按调用次数降序的聚合行
     */
    List<AppKeyTrafficRow> selectAppKeyTraffic(@Param("since") LocalDateTime since,
                                               @Param("routerGroup") String routerGroup,
                                               @Param("restrictOwner") boolean restrictOwner,
                                               @Param("ownerUserId") String ownerUserId,
                                               @Param("limit") int limit);

    /**
     * 入站流量总计（最近窗口内、指定路由组）。
     *
     * <p>过滤条件与 {@link #selectAppKeyTraffic} 完全一致（含 app_key 非空与归属过滤），
     * 否则「调用总数」与各 Key 之和永远对不上账，也会把别人 Key 的流量算进我方分母。</p>
     *
     * @param since        起始时间（含）
     * @param routerGroup  路由组供应商 ID
     * @param restrictOwner 是否按归属过滤（管理员为 {@code false}，可见全部）
     * @param ownerUserId  归属用户标识；{@code restrictOwner} 为真且该值为 {@code null} 时只看无主 Key
     * @return 总计行；窗口内无请求时各字段为 Null
     */
    TrafficTotalRow selectTrafficTotal(@Param("since") LocalDateTime since,
                                       @Param("routerGroup") String routerGroup,
                                       @Param("restrictOwner") boolean restrictOwner,
                                       @Param("ownerUserId") String ownerUserId);

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
