package com.chua.starter.mybatis.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties.PRE;

/**
 * 数据权限(机构帐号)配置项。
 *
 * <p>配置前缀：{@code plugin.mybatis.data.scope}（注意不是 {@code plugin.mybatis-plus.data-scope}）。
 * 启用后由数据权限拦截器按 {@code @DataScope} 注解追加过滤条件；
 * 还需容器中存在名为 {@code authService} 或 {@code defaultAuthService} 的 {@code AuthService} Bean。</p>
 *
 * <p>完整 YAML 配置示例（未配置项使用字段默认值）：</p>
 *
 * <pre>
 * plugin:
 *   mybatis:
 *     data:
 *       scope:
 *         enable: true                  # 是否启用数据权限拦截（默认 false）
 *         table-name: sys_dept          # 部门表名
 *         dept-id-column: sys_dept_id   # 部门 ID 列名
 *         dept-tree-id-column: sys_dept_tree_id   # 部门树 ID 列名（DEPT_AND_SUB 前缀匹配用）
 *         current-user-id-column: create_by       # 当前用户 ID 列名（SELF 过滤用）
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = PRE)
public class MybatisPlusDataScopeProperties {


    /**
     * PRE
    */
    public static final String PRE = "plugin.mybatis.data.scope";

    /**
     * 是否启用。
     *
     * <p>默认值：{@code false}。还需容器存在名为 {@code authService} / {@code defaultAuthService}
     * 的 {@code AuthService} Bean，数据权限拦截才会真正生效。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     data:
     *       scope:
     *         enable: true
     * </pre>
     */
    private boolean enable = false;
    /**
     * 表名（部门表）。
     *
     * <p>默认值：{@code sys_dept}。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     data:
     *       scope:
     *         table-name: sys_dept
     * </pre>
     */
    private String tableName = "sys_dept";

    /**
     * 字段名（部门 ID 列）。
     *
     * <p>默认值：{@code sys_dept_id}。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     data:
     *       scope:
     *         dept-id-column: sys_dept_id
     * </pre>
     */
    private String deptIdColumn = "sys_dept_id";

    /**
     * 树标识（部门树 ID 列，{@code DEPT_AND_SUB} 前缀匹配用）。
     *
     * <p>默认值：{@code sys_dept_tree_id}。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     data:
     *       scope:
     *         dept-tree-id-column: sys_dept_tree_id
     * </pre>
     */
    private String deptTreeIdColumn = "sys_dept_tree_id";

    /**
     * 当前用户标识（当前用户 ID 列，{@code SELF} 过滤用）。
     *
     * <p>默认值：{@code create_by}。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     data:
     *       scope:
     *         current-user-id-column: create_by
     * </pre>
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
