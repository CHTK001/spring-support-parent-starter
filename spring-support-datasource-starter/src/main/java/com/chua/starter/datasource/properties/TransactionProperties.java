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
 *     enable: true                                    # 是否启用声明式事务(默认 false)
 *     timeout: 10                                     # 事务超时时间(秒，默认 10)
 *     tx-mapper: "com.example.**.service.impl.*"      # 事务切入点表达式(缺省按主包生成)
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
     * 是否启用声明式事务
     * 默认值: false
     * 示例: plugin.transaction.enable=true
     */
    private boolean enable = false;


    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.transaction";
    /**
     * 事务超时时间（秒）
     * 默认值: 10
     * 示例: plugin.transaction.timeout=10
     */
    // [P3C A20 豁免]
    private Integer timeout = 10;
    /**
     * 事务切点表达式
     * <p>缺省时按主包自动生成 {@code execution(* <主包>..*..*(..))}。</p>
     * 默认值: ""
     * 示例: plugin.transaction.tx-mapper=execution(* com.example.service..*..*(..))
     */
    private String txMapper = "";
    /**
     * 只读事务方法名前缀（逗号分隔）
     * <p>缺省时内置 {@code get*,query*,find*,list*,count*,exist*,search*,fetch*}。</p>
     * 默认值: ""
     * 示例: plugin.transaction.read-only=get*,query*,find*,list*
     */
    private String readOnly = "";
    /**
     * 无事务方法名前缀（逗号分隔）
     * <p>缺省时内置 {@code noTx*}。</p>
     * 默认值: ""
     * 示例: plugin.transaction.no-tx=noTx*
     */
    private String noTx = "";
    /**
     * 写事务方法名前缀（逗号分隔）
     * <p>缺省时内置 {@code add*,save*,insert*,update*,modify*,delete*,remove*}。</p>
     * 默认值: ""
     * 示例: plugin.transaction.write-only=add*,save*,insert*,update*,delete*
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
