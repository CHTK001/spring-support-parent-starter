package com.chua.starter.datasource.properties;

import com.chua.common.support.lang.engine.ddl.ActionType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 自动建表配置属性
 * <p>
 * 用于配置自动建表功能，支持根据实体类自动创建或更新数据库表结构。
 * </p>
 *
 * <h3>配置示例：</h3>
 * <pre>
 * plugin:
 *   table:
 *     enable: true
 *     async: true
 *     packages:
 *       - com.example.entity
 *     type: UPDATE
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
@Data
@ConfigurationProperties(prefix = CreateTableProperties.PRE, ignoreInvalidFields = true)
public class CreateTableProperties {
    
    public static final String PRE = "plugin.table";

    /**
     * 是否启用自动建表功能
     */
    private boolean enable = false;

    /**
     * 是否异步执行建表操作
     * <p>
     * 建议在生产环境开启，避免阻塞应用启动
     * </p>
     */
    private boolean async = false;

    /**
     * 需要扫描的实体类包路径
     */
    private String[] packages;

    /**
     * 建表动作类型
     * <ul>
     *   <li>CREATE - 仅创建不存在的表</li>
     *   <li>UPDATE - 更新表结构（默认）</li>
     *   <li>DROP_CREATE - 删除后重建</li>
     * </ul>
     */
    private ActionType type = ActionType.UPDATE;
}
