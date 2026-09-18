package com.chua.starter.mybatis.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties.PRE;

/**
* 数据权限(机构帐号)
*
* @author CH
* @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = PRE)
public class MybatisPlusDataScopeProperties {


    /** PRE */
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
    * 树标识
    */
    private String deptTreeIdColumn = "sys_dept_tree_id";

    /**
    * 当前用户标识
    */
    private String currentUserIdColumn = "create_by";

    /**
    * 是否启用
    *
    * @return 是否启用
    */
    public boolean isEnable() {
        return enable;
    }

    /**
    * 获取表名
    *
    * @return 表名
    */
    public String getTableName() {
        return tableName;
    }

    /**
    * 获取部门标识列名
    *
    * @return 部门ID列名
    */
    public String getDeptIdColumn() {
        return deptIdColumn;
    }

    /**
    * 获取部门树标识列名
    *
    * @return 部门树ID列名
    */
    public String getDeptTreeIdColumn() {
        return deptTreeIdColumn;
    }

    /**
    * 获取当前用户标识列名
    *
    * @return 当前用户ID列名
    */
    public String getCurrentUserIdColumn() {
        return currentUserIdColumn;
    }
}
