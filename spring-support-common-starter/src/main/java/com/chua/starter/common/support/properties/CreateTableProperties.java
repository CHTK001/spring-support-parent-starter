package com.chua.starter.common.support.properties;

import com.chua.common.support.lang.engine.ddl.ActionType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 自动建表配置属性
 * <p>
 * 用于配置自动建表功能，支持根据实体类自动创建或更新数据库表结构。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 */
@Data
@ConfigurationProperties(prefix = CreateTableProperties.PRE, ignoreInvalidFields = true)
public class CreateTableProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.table";

    /**
     * 是否开启自动建表
     */
    private boolean enable;

    /**
     * 异步建表
     */
    private boolean async;

    /**
     * 包装
     */
    private String[] packages;

    /**
     * 类型
     */
    private ActionType type = ActionType.UPDATE;
}

