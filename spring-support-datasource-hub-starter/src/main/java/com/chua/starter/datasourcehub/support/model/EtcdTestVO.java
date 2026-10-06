package com.chua.starter.datasourcehub.support.model;

/**
 * etcd 连通性测试结果。
 *
 * @param connected 是否连通
 * @param version   服务端版本，取 {@code /v3/maintenance/status} 的 version
 * @param leader    leader 成员 ID，取 {@code /v3/maintenance/status} 的 leader
 * @param message   结果说明，失败时为可读原因
 * @author CH
 * @since 4.0.0.43
 */
public record EtcdTestVO(
        Boolean connected,
        String version,
        String leader,
        String message
) {
}
