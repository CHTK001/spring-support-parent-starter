// [P3C BL-边界 豁免]

package com.chua.starter.datasourcehub.support.model;

import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;

import java.time.LocalDateTime;

/**
 * 数据源连通性测试结果。
 *
 * <p>延迟口径：连接延迟 = 建立连接耗时（TCP 建链 + 协议握手）；
 * 会话延迟 = 已建立连接上一次往返耗时（jdbc 跑 {@code SELECT 1}、redis 跑 {@code PING}、
 * 其余类型做一次 TCP 探活）。测试失败时两个延迟均为 null，不把失败耗时当成功指标。</p>
 *
 * @param success            是否连通
 * @param message            结果说明
 * @param type               被测试的数据源类型
 * @param connectLatencyMs   连接延迟（毫秒），未测出时为 null
 * @param sessionLatencyMs   会话延迟（毫秒），未测出时为 null
 * @param tunneled           本次测试是否经由 SSH 隧道中转；<b>隧道打不开时为 null</b>（未判定）
 * @param testedAt           测试时间
 * @param connectionCount    采样时刻目标服务上的客户端连接数；类型不支持时为 null
 * @param serverStats        采样时刻服务器运行指标；类型不支持时为 null
 * @param version            探测出的数据库服务端版本（如 {@code 8.0.36}）；探测不出时为 null
 * @author CH
 * @since 4.0.0.42
 */
public record DataSourceTestVO(
        boolean success,
        String message,
        String type,
        Long connectLatencyMs,
        Long sessionLatencyMs,
        Boolean tunneled,
        LocalDateTime testedAt,
        Long connectionCount,
        DatasourceProbeSupport.ServerStats serverStats,
        String version
) {

    /**
     * 不带服务端版本的构造：无服务器指标的类型照旧填前九项。
     *
     * @param success          是否连通
     * @param message          结果说明
     * @param type             被测试的数据源类型
     * @param connectLatencyMs 连接延迟（毫秒）
     * @param sessionLatencyMs 会话延迟（毫秒）
     * @param tunneled         本次测试是否经由 SSH 隧道中转
     * @param testedAt         测试时间
     * @param connectionCount  采样时刻目标服务上的客户端连接数
     * @param serverStats      采样时刻服务器运行指标
     */
    public DataSourceTestVO(
            boolean success,
            String message,
            String type,
            Long connectLatencyMs,
            Long sessionLatencyMs,
            Boolean tunneled,
            LocalDateTime testedAt,
            Long connectionCount,
            DatasourceProbeSupport.ServerStats serverStats
    ) {
        this(success, message, type, connectLatencyMs, sessionLatencyMs, tunneled, testedAt,
                connectionCount, serverStats, null);
    }

    /**
     * 无服务器指标、无版本号的五参之后的兼容构造：不采集服务器指标的类型照旧填前八项。
     *
     * @param success          是否连通
     * @param message          结果说明
     * @param type             被测试的数据源类型
     * @param connectLatencyMs 连接延迟（毫秒）
     * @param sessionLatencyMs 会话延迟（毫秒）
     * @param tunneled         本次测试是否经由 SSH 隧道中转
     * @param testedAt         测试时间
     * @param connectionCount  采样时刻目标服务上的客户端连接数
     */
    public DataSourceTestVO(
            boolean success,
            String message,
            String type,
            Long connectLatencyMs,
            Long sessionLatencyMs,
            Boolean tunneled,
            LocalDateTime testedAt,
            Long connectionCount
    ) {
        this(success, message, type, connectLatencyMs, sessionLatencyMs, tunneled,
                testedAt, connectionCount, null, null);
    }
}
