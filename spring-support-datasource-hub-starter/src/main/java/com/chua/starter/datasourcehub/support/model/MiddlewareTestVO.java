package com.chua.starter.datasourcehub.support.model;

/**
 * 中间件连通性测试结果
 *
 * <p>Nacos、InfluxDB、ZooKeeper 等中间件的测试连接端点共用该结构，
 * 版本字段在服务未开放版本接口时为 null，随全局 NON_NULL 策略从 JSON 中省略。</p>
 *
 * @param connected 是否连通
 * @param version   中间件版本，取不到时为 null
 * @param message   结果说明，失败时为原因
 * @author CH
 * @since 4.0.0.42
 */
public record MiddlewareTestVO(
        Boolean connected,
        String version,
        String message
) {
}
