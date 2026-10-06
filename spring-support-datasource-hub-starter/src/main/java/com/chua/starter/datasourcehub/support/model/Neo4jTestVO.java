package com.chua.starter.datasourcehub.support.model;

/**
 * Neo4j 连通性测试结果
 *
 * <p>相比其它中间件多一个 {@code edition}：社区版与企业版在权限、集群视图上的可用接口不同，
 * 页面据此决定后续能力提示。版本字段取不到时为 null，随全局 NON_NULL 策略从 JSON 中省略。</p>
 *
 * @param connected 是否连通且凭据可用
 * @param version   服务端版本，取不到时为 null
 * @param edition   版本类型（community/enterprise），取不到时为 null
 * @param message   结果说明，失败时为原因
 * @author CH
 * @since 4.0.0.42
 */
public record Neo4jTestVO(
        Boolean connected,
        String version,
        String edition,
        String message
) {
}
