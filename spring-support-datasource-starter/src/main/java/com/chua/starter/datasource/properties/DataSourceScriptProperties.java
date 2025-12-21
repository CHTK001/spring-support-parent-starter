package com.chua.starter.datasource.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * 数据源脚本配置属性类
 * <p>
 * 配置示例:
 * <pre>
 * plugin:
 *   datasource:
 *     script:
 *       enable: true                              # 是否启用脚本功能
 *       script-path: "classpath*:db/migration/*.sql" # 脚本文件路径
 *       database-type: "mysql"                   # 数据库类型(可选)
 *       version-table: "flyway_schema_history"    # 版本记录表名
 *       baseline-version: "1.0.0"                # 基线版本
 *       baseline-description: "Initial setup"    # 基线描述
 *       baseline-on-migrate: true                 # 是否在迁移时创建基线
 *       script-prefix: "V"                       # 脚本文件前缀
 *       script-suffix: ".sql"                    # 脚本文件后缀
 *       version-separator: "__"                  # 版本分隔符
 *       separator: ";"                           # SQL语句分隔符
 *       sql-script-encoding: "UTF-8"             # 脚本编码
 *       continue-on-error: false                  # 出错时是否继续
 *       ignore-failed-drops: true                # 是否忽略DROP失败
 *       validate-checksum: true                   # 是否验证校验和
 *       mixed: false                              # 是否允许混合模式
 *       timeout: 300                              # 执行超时时间(秒)
 *       verbose: true                             # 是否输出详细日志
 *       clean-disabled: true                      # 是否禁用清理
 *       release-type: STABLE                       # 允许执行的版本类型(SNAPSHOT/ALPHA/BETA/RC/STABLE/ALL)
 *       allowed-release-types: STABLE,RC           # 允许执行的版本类型集合(可选,覆盖release-type)
 * </pre>
 *
 * @author CH
 * @since 2025/9/3 9:05
 */
@Data
@ConfigurationProperties(prefix = DataSourceScriptProperties.PRE)
public class DataSourceScriptProperties {

    /**
     * 配置属性前缀
     */
    public static final String PRE = "plugin.datasource.script";

    /**
     * 是否启用数据源脚本功能
     * 默认值: true
     * 示例: plugin.datasource.script.enable=true
     */
    private boolean enable = true;

    /**
     * 是否继续执行脚本，如果某个脚本执行失败，是否继续执行下一个脚本
     * 默认值: false
     * 示例: plugin.datasource.script.continue-on-error=true
     */
    private boolean continueOnError = true;
    /**
     * 是否忽略失败的DROP语句
     * 默认值: true
     * 示例: plugin.datasource.script.ignore-failed-drops=true
     */
    private boolean ignoreFailedDrops = true;


    /**
     * 脚本分隔符
     * 默认值: ;
     * 示例: plugin.datasource.script.separator=;
     */
    private String separator = ";";

    /**
     * 脚本编码
     * 默认值: UTF-8
     * 示例: plugin.datasource.script.encoding=UTF-8
     */
    private String sqlScriptEncoding = "UTF-8";
    /**
     * 数据源脚本路径
     * 默认值: classpath*:db/init/*.sql
     * 示例: plugin.datasource.script.script-path=classpath*:db/migration/*.sql
     */
    private String scriptPath = "classpath*:db/init/*.sql";

    /**
     * 数据库类型
     * 用于指定特定数据库的脚本目录
     * 默认值: null (不指定数据库类型)
     * 示例: plugin.datasource.script.database-type=mysql
     */
    private String databaseType;

    /**
     * 是否在迁移时生成基线
     * 默认: true
     * 示例: plugin.datasource.script.baseline-on-migrate=false
     */
    private boolean baselineOnMigrate = true;

    /**
     * 是否禁用清理
     * 默认: true
     * 示例: plugin.datasource.script.clean-disabled=false
     */
    private boolean cleanDisabled = true;

    /**
     * 版本记录表名
     * 默认值: sys_database_version
     * 示例: plugin.datasource.script.version-table=flyway_schema_history
     */
    private String versionTable = "sys_database_version";

    /**
     * 基线版本号
     * 默认值: 1.0.0
     * 示例: plugin.datasource.script.baseline-version=1.0.0
     */
    private String baselineVersion = "1.0.0";

    /**
     * 基线描述
     * 默认值: Initial baseline
     * 示例: plugin.datasource.script.baseline-description=Initial setup
     */
    private String baselineDescription = "Initial baseline";

    /**
     * 脚本文件名前缀
     * 默认值: V
     * 示例: plugin.datasource.script.script-prefix=V
     */
    private String scriptPrefix = "V";

    /**
     * 脚本文件名后缀
     * 默认值: .sql
     * 示例: plugin.datasource.script.script-suffix=.sql
     */
    private String scriptSuffix = ".sql";

    /**
     * 版本分隔符
     * 默认值: __
     * 示例: plugin.datasource.script.version-separator=__
     */
    private String versionSeparator = "__";

    /**
     * 是否验证脚本校验和
     * 默认值: true
     * 示例: plugin.datasource.script.validate-checksum=false
     */
    private boolean validateChecksum = true;

    /**
     * 是否允许混合模式（允许非版本化脚本）
     * 默认值: false
     * 示例: plugin.datasource.script.mixed=true
     */
    private boolean mixed = false;

    /**
     * 脚本执行超时时间（秒）
     * 默认值: 0（无超时）
     * 示例: plugin.datasource.script.timeout=300
     */
    private int timeout = 0;

    /**
     * 是否输出详细日志
     * 默认值: true
     * 示例: plugin.datasource.script.verbose=true
     */
    private boolean verbose = true;

    /**
     * 允许执行的版本类型
     * 默认值: STABLE（只执行正式版本）
     * 示例: plugin.datasource.script.release-type=STABLE
     * 可选值: SNAPSHOT, ALPHA, BETA, RC, STABLE, ALL
     */
    private ReleaseType releaseType = ReleaseType.STABLE;

    /**
     * 允许执行的版本类型集合（用于更精细控制）
     * 当设置此属性时，会覆盖 releaseType 的配置
     * 示例: plugin.datasource.script.allowed-release-types=STABLE,RC
     */
    private Set<ReleaseType> allowedReleaseTypes;

    /**
     * 初始化数据脚本(initdata)的并发执行数量
     * 使用虚拟线程并发执行 initdata 脚本
     * 默认值: 0（无限制，每个脚本一个虚拟线程）
     * 示例: plugin.datasource.script.initdata-parallelism=4
     */
    private int initdataParallelism = 0;

    /**
     * 初始化数据脚本(initdata)的等待超时时间（分钟）
     * 默认值: 5
     * 示例: plugin.datasource.script.initdata-timeout-minutes=10
     */
    private int initdataTimeoutMinutes = 5;

    /**
     * 版本发布类型枚举
     * 用于控制允许执行的脚本版本类型
     *
     * @author CH
     * @since 2025/12/4
     */
    @AllArgsConstructor
    @Getter
    public enum ReleaseType {
        
        /**
         * 快照版本
         * 示例: V1.0.0-SNAPSHOT__init_user.sql
         */
        SNAPSHOT("snapshot", 0),
        
        /**
         * Alpha 内部测试版本
         * 示例: V1.0.0-alpha__init_user.sql
         */
        ALPHA("alpha", 1),
        
        /**
         * Beta 公开测试版本
         * 示例: V1.0.0-beta__init_user.sql
         */
        BETA("beta", 2),
        
        /**
         * RC（Release Candidate）候选发布版本
         * 示例: V1.0.0-rc1__init_user.sql
         */
        RC("rc", 3),
        
        /**
         * 正式稳定版本
         * 示例: V1.0.0__init_user.sql
         */
        STABLE("stable", 4),
        
        /**
         * 允许所有版本类型
         */
        ALL("all", 99);

        /**
         * 版本类型后缀标识
         */
        private final String suffix;
        
        /**
         * 版本类型优先级（数值越高优先级越高）
         */
        private final int priority;

        /**
         * 判断当前类型是否允许执行指定类型的脚本
         *
         * @param target 目标版本类型
         * @return 是否允许执行
         */
        public boolean isAllowed(ReleaseType target) {
            if (this == ALL) {
                return true;
            }
            // 当前配置的类型及更高优先级的类型都允许执行
            return target.priority >= this.priority;
        }

        /**
         * 根据版本后缀解析版本类型
         *
         * @param versionSuffix 版本后缀（如 "-snapshot", "-beta", "-rc1"）
         * @return 版本类型
         */
        public static ReleaseType fromSuffix(String versionSuffix) {
            if (versionSuffix == null || versionSuffix.isEmpty()) {
                return STABLE;
            }
            String lowerSuffix = versionSuffix.toLowerCase();
            if (lowerSuffix.contains("snapshot")) {
                return SNAPSHOT;
            } else if (lowerSuffix.contains("alpha")) {
                return ALPHA;
            } else if (lowerSuffix.contains("beta")) {
                return BETA;
            } else if (lowerSuffix.contains("rc")) {
                return RC;
            }
            return STABLE;
        }
    }
}
