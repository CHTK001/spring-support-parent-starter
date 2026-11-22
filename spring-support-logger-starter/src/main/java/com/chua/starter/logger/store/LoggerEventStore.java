package com.chua.starter.logger.store;

import com.chua.starter.logger.event.LoggerEvent;

import java.util.List;
import java.util.Map;

/**
 * 日志事件存储契约。服务端把收到的原始事件写入本存储，查询 API 与 SSE 推送均以此为数据源。
 *
 * <p>实现方：{@link KvLoggerEventStore}（KvEngine + SeriesEngine，持久化）、
 * {@link MemoryLoggerEventStore}（有界内存，测试/开发）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface LoggerEventStore {

    /**
     * 追加一批事件（服务端收到批次后调用；实现需自行维护应用/实例登记与指标）
     *
     * @param events 事件列表，非空
     */
    void append(List<LoggerEvent> events);

    /**
     * 分页查询事件
     *
     * @param app      应用过滤，空为全部
     * @param instance 实例过滤，空为全部
     * @param level    级别过滤，可多值逗号分隔，空为全部
     * @param keyword  关键字（对 message/logger 匹配），空为不过滤
     * @param traceId  链路追踪标识过滤，空为不过滤
     * @param start    起始时间（毫秒），空为不限
     * @param end      结束时间（毫秒），空为不限
     * @param cursor   上一页返回的游标，空为第一页
     * @param limit    每页条数（1~500）
     * @return 分页结果
     */
    LoggerPage query(String app, String instance, String level, String keyword, String traceId,
                     Long start, Long end, String cursor, int limit);

    /**
     * 链路追踪摘要：按 traceId 聚合近期事件，返回条数/错误数/起止时间/涉及应用，
     * 按最近活动倒序。
     *
     * @param app   应用过滤，空为全部
     * @param limit 返回条数上限（1~200）
     * @return 摘要列表（traceId/count/errorCount/firstTs/lastTs/durationMs/apps/instances）
     */
    List<Map<String, Object>> traces(String app, int limit);

    /**
     * 统计信息：total、levels、eps（近 1 小时按分钟）、topLoggers、apps、instances
     *
     * @param app      应用过滤，空为全部
     * @param instance 实例过滤，空为全部
     * @return 统计数据
     */
    Map<String, Object> stats(String app, String instance);

    /**
     * 应用/实例清单：app、instance、host、lastSeen、total、errorCount
     *
     * @return 清单列表
     */
    List<Map<String, Object>> apps();

    /**
     * 删除事件
     *
     * @param app      应用过滤，空为全部
     * @param instance 实例过滤，空为全部
     * @param level    级别过滤（逗号分隔多值），空为全部
     * @param before   删除该时间（毫秒）之前的事件，空为不限
     * @return 删除条数
     */
    int delete(String app, String instance, String level, Long before);
}
