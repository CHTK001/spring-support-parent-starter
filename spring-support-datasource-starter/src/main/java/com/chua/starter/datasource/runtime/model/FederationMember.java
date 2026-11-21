package com.chua.starter.datasource.runtime.model;

import lombok.Data;

/**
 * Calcite 联邦成员连接参数。
 *
 * <p>前端从数据源台账选中库后展开连接参数提交，持久化于
 * ds_calcite_federation.members JSON；运行期由本模块据此独立建池，不跨模块取数。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class FederationMember {

    /**
     * 该成员在联邦中暴露的 schema 名
     */
    private String schemaName;

    /**
     * JDBC URL
     */
    private String url;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 驱动类名（空则由 DriverManager 自动探测）
     */
    private String driverClassName;
}
