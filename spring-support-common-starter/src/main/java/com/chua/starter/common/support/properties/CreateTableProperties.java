package com.chua.starter.common.support.properties;

import com.chua.common.support.lang.engine.ddl.ActionType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 自定建表
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = CreateTableProperties.PRE, ignoreInvalidFields = true)
public class CreateTableProperties {

    public static final String PRE = "plugin.table";

    /**
     * 是否开启
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
