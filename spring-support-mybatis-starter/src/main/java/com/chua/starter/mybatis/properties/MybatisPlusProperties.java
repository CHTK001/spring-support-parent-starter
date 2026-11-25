package com.chua.starter.mybatis.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static com.chua.starter.mybatis.properties.MybatisPlusProperties.PRE;

/**
 * MyBatis-Plus 增强装配配置项。
 *
 * <p>配置前缀：{@code plugin.mybatis}。与 {@link MybatisPlusDataScopeProperties}（前缀
 * {@code plugin.mybatis.data.scope}）、{@link MybatisSlowSqlProperties}（前缀
 * {@code plugin.mybatis.slow-sql}）共同构成模块全部配置。</p>
 *
 * <p>完整 YAML 配置示例（所有项均可省略，省略时使用字段默认值）：</p>
 *
 * <pre>
 * plugin:
 *   mybatis:
 *     enable: true                  # 预留开关，模块内暂无消费方，写 false 不会关闭模块
 *     read-only: false              # 只读模式：true 时写操作抛 ReadOnlyException
 *     open-xml-reload: true         # XML 热重载开关，须显式 true 才注册 mapper-reload
 *     reload-type: AUTO             # 热重载方式：NONE 不刷新 / AUTO 自动监听
 *     reload-time: 3000             # 热重载轮询间隔（毫秒）
 *     reload-directories:           # 热重载监听目录列表
 *       - path: D:/mapper           # 目录：本地路径或 classpath: 前缀路径
 *         pattern: "**&#47;*Mapper.xml"     # 文件匹配模式
 *         watch-enabled: true       # 是否启用文件监听
 *     sql-method:                   # 自定义 SQL 注入来源列表
 *       - type: FILE                # SQL 来源：FILE 文件 / MYSQL 数据源 Bean
 *         source: sql/extend.sql    # 文件路径或数据源 Bean 名
 *         timeout: 3000             # watchdog 超时时间（毫秒）
 *         watchdog: false           # 是否开启变更检测
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = PRE)
public class MybatisPlusProperties {
    /**
     * 是否启用。
     *
     * <p>默认值：{@code true}。当前为预留开关，模块内无任何消费方，
     * 配置为 {@code false} 不会关闭本模块；如需停用请移除本 starter 依赖。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     enable: true
     * </pre>
     */
    private boolean enable = true;



    /**
     * PRE
    */
    public static final String PRE = "plugin.mybatis";

    /**
     * xml是否可热加载。
     *
     * <p>默认值：{@code true}。注意：{@code mapper-reload} Bean 的装配条件为
     * {@code havingValue="true", matchIfMissing=false}，因此不显式配置为 {@code true}
     * 时不会注册热重载 Bean 与 {@code /actuator/mybatis-extension} 端点。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     open-xml-reload: true
     * </pre>
     */
    private boolean openXmlReload = true;

    /**
     * 是否开启只读模式。
     * 开启后所有更新、插入、删除操作都会抛出只读异常。
     *
     * <p>默认值：{@code false}。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     read-only: false
     * </pre>
     */
    private boolean readOnly = false;

    /**
     * 是否开启只读模式
     *
     * @return 是否只读
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 映射器加载源。
     *
     * <p>默认值：无（{@code null}），不注入任何自定义 SQL 方法。注意：当前
     * {@code SupportInjector} 仅注册为 Bean、未通过 {@code setSqlInjector} 挂入
     * {@code SqlSessionFactory}，该配置暂不生效。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     sql-method:
     *       - type: FILE
     *         source: sql/extend.sql
     *         timeout: 3000
     *         watchdog: false
     * </pre>
     */
    private List<SqlMethodProperties> sqlMethod;

    /**
     * 获取SQL方法配置列表
     *
     * @return SQL方法配置列表
     */
    public List<SqlMethodProperties> getSqlMethod() {
        return sqlMethod;
    }

    /**
     * 获取重载类型
     *
     * @return 重载类型
     */
    public ReloadType getReloadType() {
        return reloadType;
    }

    /**
     * 获取重载目录列表
     *
     * @return 重载目录列表
     */
    public List<ReloadDirectory> getReloadDirectories() {
        return reloadDirectories;
    }

    /**
     * 获取重载时间
     *
     * @return 重载时间
     */
    public int getReloadTime() {
        return reloadTime;
    }
    /**
     * 自动热加载方式。
     *
     * <p>默认值：{@code AUTO}。可选 {@code NONE}（不刷新）/ {@code AUTO}（自动监听文件变更）；
     * 配置为 {@code NONE} 时不启动自动监听，仅可通过端点手动重载。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     reload-type: AUTO
     * </pre>
     */
    private ReloadType reloadType = ReloadType.AUTO;
    /**
     * 自动热加载时间。
     *
     * <p>默认值：{@code 3000}（毫秒），用作热重载文件监听的轮询间隔。
     * 代码未对 ≤0 做回落处理，请配置为正整数。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     reload-time: 3000
     * </pre>
     */
    private int reloadTime = 3_000;

    /**
     * 热重载监听目录配置。
     * 支持本地文件系统路径和类路径路径。
     * 本地路径示例: /路径/转为/映射器, D:/映射器
     * 类路径路径示例: 类路径:映射器, 类路径*:映射器/**
     *
     * <p>默认值：无（{@code null}），此时仅扫描类路径下的 **&#47;*Mapper.xml 资源；
     * 列表为空或条目 {@code path} 为空时对应条目被跳过。</p>
     *
     * <pre>
     * plugin:
     *   mybatis:
     *     reload-directories:
     *       - path: D:/mapper
     *         pattern: "**&#47;*Mapper.xml"
     *         watch-enabled: true
     * </pre>
     */
    private List<ReloadDirectory> reloadDirectories;


    /**
     * SQL方法属性类。
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class SqlMethodProperties {
        /**
         * SQL来源。
         *
         * <p>默认值：{@code null}，未配置时按 {@code MYSQL} 处理。
         * 可选值：{@code FILE}（文件）/ {@code MYSQL}（MySQL 数据源 Bean）。</p>
         *
         * <pre>
         * plugin:
         *   mybatis:
         *     sql-method:
         *       - type: FILE
         * </pre>
         */
        private SqlMethodType type;

        /**
         * 源信息。
         *
         * <p>默认值：{@code null}。{@code type=FILE} 时为文件路径；
         * {@code type=MYSQL} 时为数据源 Bean 名。</p>
         *
         * <pre>
         * plugin:
         *   mybatis:
         *     sql-method:
         *       - type: FILE
         *         source: sql/extend.sql
         * </pre>
         *
         * @see SqlMethodType#FILE 文件路径
         * @see SqlMethodType#MYSQL mysql datasource bean
         */
        private String source;
        /**
         * watchdog超时时间。
         *
         * <p>默认值：{@code 3000}（毫秒）。代码未对 ≤0 做回落处理，请配置为正整数。</p>
         *
         * <pre>
         * plugin:
         *   mybatis:
         *     sql-method:
         *       - timeout: 3000
         * </pre>
         */
        private int timeout = 3_000;
        /**
         * 是否开启检测。
         *
         * <p>默认值：{@code false}。</p>
         *
         * <pre>
         * plugin:
         *   mybatis:
         *     sql-method:
         *       - watchdog: false
         * </pre>
         */
        private boolean watchdog;

        /**
         * 获取源信息
         *
         * @return 源信息
         */
        public String getSource() {
            return source;
        }

        /**
         * 获取类型
         *
         * @return 类型
         */
        public SqlMethodType getType() {
            return type;
        }

        /**
         * 获取超时时间
         *
         * @return 超时时间
         */
        public int getTimeout() {
            return timeout;
        }

        /**
         * 是否开启watchdog
         *
         * @return 是否开启watchdog
         */
        public boolean isWatchdog() {
            return watchdog;
        }
    }

    /**
     * 方法类型
     * @author CH
     * @since 4.0.0
     */
    public enum SqlMethodType {
        /**
         * 文件
         */
        FILE,
        /**
         * MySQL
         */
        MYSQL
    }

    /**
     * 类型
     * @author CH
     * @since 4.0.0
     */
    public enum ReloadType {
        /**
         * 不刷新
         */
        NONE,
        /**
         * 自动刷新
         */
        AUTO
    }

    /**
     * 热重载目录配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class ReloadDirectory {
        /**
         * 目录路径。
         * 支持格式：
         * - 本地文件系统路径: /路径/转为/映射器, D:/映射器
         * - 类路径路径: 类路径:映射器, 类路径*:映射器/**
         *
         * <p>默认值：{@code null}；为空时该条目被跳过。</p>
         *
         * <pre>
         * plugin:
         *   mybatis:
         *     reload-directories:
         *       - path: D:/mapper
         * </pre>
         */
        private String path;

        /**
         * 是否启用监听。
         * 本地路径默认启用，类路径路径在jar包中无法监听。
         *
         * <p>默认值：{@code true}。</p>
         *
         * <pre>
         * plugin:
         *   mybatis:
         *     reload-directories:
         *       - watch-enabled: true
         * </pre>
         */
        private boolean watchEnabled = true;

        /**
         * 文件匹配模式。
         * 默认为双星号斜杠星号 映射器.xml。
         *
         * <p>默认值：**&#47;*Mapper.xml。</p>
         *
         * <pre>
         * plugin:
         *   mybatis:
         *     reload-directories:
         *       - pattern: "**&#47;*Mapper.xml"
         * </pre>
         */
        private String pattern = "**/*Mapper.xml";

        /**
         * 获取目录路径
         *
         * @return 目录路径
         */
        public String getPath() {
            return path;
        }

        /**
         * 获取文件匹配模式
         *
         * @return 文件匹配模式
         */
        public String getPattern() {
            return pattern;
        }

        /**
         * 是否启用监听
         *
         * @return 是否启用监听
         */
        public boolean isWatchEnabled() {
            return watchEnabled;
        }
    }
}
