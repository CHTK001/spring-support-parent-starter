package com.chua.starter.datasource.runtime.model;

import lombok.Data;

/**
 * 分库成员连接参数。
 *
 * <p>前端从数据源台账选中库后展开连接参数提交，持久化于
 * ds_sharding_rule.database_members JSON；运行期由本模块据此建池。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class ShardingDbMember {

    /**
     * 分库逻辑名（ShardingSphere 数据节点前缀）
     */
    private String name;

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
