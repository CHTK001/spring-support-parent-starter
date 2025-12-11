package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 事务配置
 *
 * @author CH
 * @since 2021-07-19
 */
@Data
@ConfigurationProperties(prefix = TransactionProperties.PREFIX, ignoreInvalidFields = true)
public class TransactionProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PREFIX = "plugin.transaction";
    /**
     * 事务超时时间
     */
    private Integer timeout = 10;
    /**
     * 事务位置
     */
    private String txMapper = "";
    /**
     * 只读事务前缀
     */
    private String readOnly = "";
    /**
     * 无事务
     */
    private String noTx = "";
    /**
     * 写事务
     */
    private String writeOnly = "";
    /**
     * 是否开启
     */
    private boolean enable;
}
