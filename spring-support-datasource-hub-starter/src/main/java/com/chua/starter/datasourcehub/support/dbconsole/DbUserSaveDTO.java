package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 保存数据库用户请求（新建 / 修改共用）。
 *
 * <p>新建走 {@code CREATE USER} + 可选 {@code GRANT}；修改走 {@code ALTER USER}
 * （改密码 / 资源限额 / 超级权限）加增量 {@code GRANT / REVOKE}。用户名与主机
 * 经标识符白名单校验后才进入 SQL，密码经单引号转义。</p>
 *
 * @param datasourceId 数据源 ID
 * @param username     用户名
 * @param host         允许登录的主机（{@code %}=任意，缺省按 {@code %}）
 * @param password     新密码；新建时为空表示无密码，修改时为空表示不改密码
 * @param grants       要授予的全局权限清单（如 SELECT / ALL PRIVILEGES）
 * @param superUser    是否授予超级权限（MySQL：GRANT/REVOKE SUPER ON *.*）
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbUserSaveDTO(
        Long datasourceId,
        String username,
        String host,
        String password,
        java.util.List<String> grants,
        Boolean superUser
) {
}