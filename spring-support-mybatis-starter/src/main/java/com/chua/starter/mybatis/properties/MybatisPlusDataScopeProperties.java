package com.chua.starter.mybatis.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties.PRE;

/**
 * 数据权限(机构帐号)
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE)
public class MybatisPlusDataScopeProperties {


    public static final String PRE = "plugin.mybatis.data.scope";

    /**
     * 是否启用
     */
    private boolean enable = false;
    /**
     * 表名
     */
    private String tableName = "sys_dept";

    /**
     * 字段名
     */
    private String deptIdColumn = "sys_dept_id";

    /**
     * 树id
     */
    private String deptTreeIdColumn = "sys_dept_tree_id";

    /**
     * 当前用户ID
     */
    private String currentUserIdColumn = "create_by";
}
