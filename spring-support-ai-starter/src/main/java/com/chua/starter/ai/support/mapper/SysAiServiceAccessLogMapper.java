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

    /**
     * 实例统计：服务管理页每张端口卡上的「请求 / Token / 失败 / 均耗时」。
     *
     * @param instanceId   服务实例 ID（访问日志里的 instance_id）
     * @param calls        调用次数
     * @param successCalls 成功次数（2xx/3xx）
     * @param tokens       令牌合计
     * @param avgLatencyMs 平均耗时（毫秒）；窗口内无样本为 null
     * @param lastAt       窗口内最近一次请求时间；无样本为 null
     */
    record InstanceStatRow(String instanceId, Long calls, Long successCalls,
                           Long tokens, Double avgLatencyMs, LocalDateTime lastAt) {
    }

    /**
     * 按实例聚合最近窗口内的访问日志。
     *
     * <p>只统计带 {@code instance_id} 的行：网关直连（控制台 / 对外 API）不归属任何
     * 服务实例，算进任何一张端口卡都会让「这个实例的请求数」虚高。</p>
     *
     * @param since 起始时间（含）
     * @return 每个实例一行
     */
    List<InstanceStatRow> selectInstanceStats(@Param("since") LocalDateTime since);

    /**
     * 实例请求量分桶序列（端口卡的迷你折线）。
     *
     * @param since         起始时间（含）
     * @param bucketMinutes 每个桶的分钟数（窗口/24，至少 1）
     * @return 每个实例每个桶一行；没有请求的桶不返回，由调用方补 0
     */
    List<InstanceSeriesRow> selectInstanceSeries(@Param("since") LocalDateTime since,
                                                 @Param("bucketMinutes") int bucketMinutes);

    /**
     * 实例 P95 耗时（成功率之外最常被问的一个数）。
     *
     * @param since 起始时间（含）
     * @return 每个实例一行；没有样本的实例不返回
     */
    List<InstanceP95Row> selectInstanceP95(@Param("since") LocalDateTime since);

    /**
     * 实例分桶序列行。
     *
     * @param instanceId 服务实例 ID
     * @param bucket     桶序号（0 起）
     * @param calls      该桶调用次数
     */
    record InstanceSeriesRow(String instanceId, Integer bucket, Long calls) {
    }

    /**
     * 实例 P95 行。
     *
     * @param instanceId 服务实例 ID
     * @param p95Ms      P95 耗时（毫秒）
     */
    record InstanceP95Row(String instanceId, Long p95Ms) {
    }
}
