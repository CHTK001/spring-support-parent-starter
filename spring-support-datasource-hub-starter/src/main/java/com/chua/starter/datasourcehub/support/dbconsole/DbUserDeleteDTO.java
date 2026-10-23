package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 删除数据库用户请求。
 *
 * @param datasourceId 数据源 ID
 * @param username     用户名
 * @param host         允许登录的主机（{@code %}=任意）
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbUserDeleteDTO(
        Long datasourceId,
        String username,
        String host
) {
}