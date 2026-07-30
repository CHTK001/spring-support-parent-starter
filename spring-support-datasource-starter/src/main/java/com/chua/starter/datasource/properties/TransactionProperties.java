package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 事务配置
 * <p>
 * 配置示例:
 * <pre>
 * plugin:
 *   transaction:
 *     enable: true                                    # 是否启用声明式事务
 *     timeout: 30                                     # 事务超时时间(秒)
 *     tx-mapper: "com.example.**.service.impl.*"      # 事务切入点表达式
 *     read-only: "get*,query*,find*,select*,list*"    # 只读事务方法前缀(逗号分隔)
 *     no-tx: "log*,async*"                            # 无事务方法前缀(逗号分隔)
 *     write-only: "save*,insert*,add*,update*,delete*,remove*" # 写事务方法前缀(逗号分隔)
 * </pre>
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

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    public boolean isEnable() {
        return enable;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public String getTxMapper() {
        return txMapper;
    }

    public String getReadOnly() {
        return readOnly;
    }

    public String getNoTx() {
        return noTx;
    }

    public String getWriteOnly() {
        return writeOnly;
    }

}
