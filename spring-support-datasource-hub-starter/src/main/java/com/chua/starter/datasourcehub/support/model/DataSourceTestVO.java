package com.chua.starter.datasourcehub.support.model;

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
 * @param tunneled           本次测试是否经由 SSH 隧道中转
 * @param testedAt           测试时间
 * @author CH
 * @since 4.0.0.42
 */
public record DataSourceTestVO(
        boolean success,
        String message,
        String type,
        Long connectLatencyMs,
        Long sessionLatencyMs,
        boolean tunneled,
        LocalDateTime testedAt
) {
}
