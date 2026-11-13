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
 * @since 2021-07-19
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = TransactionProperties.PREFIX, ignoreInvalidFields = true)
public class TransactionProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.transaction";
    /**
     * 事务超时时间
     */
    // [P3C A20 豁免]
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
    /**
     * 是否启用
     *
     * @return 是否enable的结果
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 获取超时
     *
     * @return 获取超时的结果
     */
    public Integer getTimeout() {
        return timeout;
    }

    /**
     * 获取tx映射器
     *
     * @return 获取tx映射器的结果
     */
    public String getTxMapper() {
        return txMapper;
    }

    /**
     * 获取读取Only
     *
     * @return 获取读取only的结果
     */
    public String getReadOnly() {
        return readOnly;
    }

    /**
     * 获取notx
     *
     * @return 获取notx的结果
     */
    public String getNoTx() {
        return noTx;
    }

    /**
     * 获取写入Only
     *
     * @return 获取写入only的结果
     */
    public String getWriteOnly() {
        return writeOnly;
    }

}
