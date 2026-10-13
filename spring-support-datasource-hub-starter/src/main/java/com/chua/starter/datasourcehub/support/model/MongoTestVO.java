package com.chua.starter.datasourcehub.support.model;

/**
 * MongoDB 连通性测试结果
 *
 * @param connected 是否连通且凭据可用
 * @param version   服务端版本（buildInfo），取不到时为 null
 * @param message   结果说明，失败时为可读原因
 * @author CH
 * @since 4.0.0.42
 */
public record MongoTestVO(
        Boolean connected,
        String version,
        String message
) {
}
