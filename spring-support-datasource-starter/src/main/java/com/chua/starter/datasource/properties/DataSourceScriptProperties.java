package com.chua.starter.datasource.properties;

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
 *       scan-mode: ONCE                           # 表结构脚本扫描模式: NONE/ONCE/ALWAYS
 *       migration-scan-mode: ALWAYS               # 升级补丁脚本扫描模式: NONE/ALWAYS
 *       data-scan-mode: ONCE                      # 初始化数据脚本扫描模式: NONE/ONCE/ALWAYS
 *       script-path: "classpath*:db/init/*.sql"   # 推荐脚本路径
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
 * @since 2025/9/3 9:05
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = DataSourceScriptProperties.PRE)
public class DataSourceScriptProperties {

    /**
     * 配置属性前缀
     */
    public static final String PRE = "plugin.datasource.script";
    /**
     * 默认扫描路径
     * <p>
     * 推荐各业务模块将结构初始化脚本与初始化数据脚本统一放在 db/初始化 目录：
     * <ul>
     *   <li>V1.0__init_xxx_complete.sql</li>
     *   <li>V1.0__initdata_xxx.sql</li>
     * </ul>
     * migration / 同步 路径仍然保留兼容能力，但更推荐在业务模块内收敛到单一 初始化 目录。
     * </p>
     */
    public static final String DEFAULT_SCRIPT_PATH =
            "classpath*:db/init/*.sql,classpath*:db/migration/*.sql,classpath*:db/sync/*.sql";

    /**
     * 是否启用数据源脚本功能
     * 默认值: true
     * 示例: plugin.datasource.script.enable=true
     */
    private boolean enable = true;

    /**
     * 表结构脚本扫描模式
     * <p>
     * 无   : 不扫描
     * ONCE   : 仅当当前数据库没有任何业务表时扫描一次
     * ALWAYS : 每次启动都扫描
     * </p>
     * 默认值: ONCE
     * 示例: plugin.datasource.script.扫描-mode=ALWAYS
     */
    private ScanMode scanMode = ScanMode.ONCE;

    /**
     * 升级补丁脚本扫描模式
     * <p>
     * 无   : 不扫描
     * ALWAYS : 每次启动都扫描
     * </p>
     * 默认值: ALWAYS
     * 示例: plugin.datasource.script.migration-扫描-mode=无
     */
    private RepeatableScanMode migrationScanMode = RepeatableScanMode.ALWAYS;

    /**
     * 初始化数据脚本扫描模式
     * <p>
     * 无   : 不扫描
     * ONCE   : 仅当当前数据库不存在业务数据时扫描一次
     * ALWAYS : 每次启动都扫描
     * </p>
     * 默认值: ONCE
     * 示例: plugin.datasource.script.数据-扫描-mode=ALWAYS
     */
    private ScanMode dataScanMode = ScanMode.ONCE;

    /**
     * 是否继续执行脚本，如果某个脚本执行失败，是否继续执行下一个脚本
     * 默认值: false
     * 示例: plugin.datasource.script.继续-on-错误=true
     */
    private boolean continueOnError = true;
    /**
     * 是否忽略失败的掉落语句
     * 默认值: true
     * 示例: plugin.datasource.script.ignore-失败-掉落=true
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
     * 示例: plugin.datasource.script.编码=UTF-8
     */
    private String sqlScriptEncoding = "UTF-8";
    /**
     * 数据源脚本路径
     * <p>
     * 支持多个路径，使用英文逗号、分号或换行分隔。
     * 推荐值：类路径*:db/初始化/*.SQL
     * </p>
     * 默认值: 类路径*:db/初始化/*.SQL,类路径*:db/migration/*.SQL,类路径*:db/同步/*.SQL
     * 示例: plugin.datasource.script.script-路径=类路径*:db/初始化/*.SQL
     */
    private String scriptPath = DEFAULT_SCRIPT_PATH;

    /**
     * 数据库类型
     * 用于指定特定数据库的脚本目录
     * 默认值: 空 (不指定数据库类型)
     * 示例: plugin.datasource.script.database-类型=MySQL
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
     * 示例: plugin.datasource.script.clean-已禁用=false
     */
    private boolean cleanDisabled = true;

    /**
     * 版本记录表名
     * 默认值: sys_database_版本
     * 示例: plugin.datasource.script.版本-table=Flyway_模式_历史
     */
    private String versionTable = "sys_database_version";

    /**
     * 基线版本号
     * 默认值: 1.0.0
     * 示例: plugin.datasource.script.baseline-版本=1.0.0
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
     * 示例: plugin.datasource.script.script-前缀=V
     */
    private String scriptPrefix = "V";

    /**
     * 脚本文件名后缀
     * 默认值: .SQL
     * 示例: plugin.datasource.script.script-后缀=.SQL
     */
    private String scriptSuffix = ".sql";

    /**
     * 版本分隔符
     * 默认值: __
     * 示例: plugin.datasource.script.版本-separator=__
     */
    private String versionSeparator = "__";

    /**
     * 是否验证脚本校验和
     * 默认值: true
     * 示例: plugin.datasource.script.校验-校验和=false
     */
    private boolean validateChecksum = true;

    /**
     * 是否允许混合模式（允许非版本化脚本）
     * 默认值: false
     * 示例: plugin.datasource.script.混合=true
     */
    private boolean mixed = false;

    /**
     * 脚本执行超时时间（秒）
     * 默认值: 0（无超时）
     * 示例: plugin.datasource.script.超时=300
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
     * 示例: plugin.datasource.script.release-类型=st
     * 可选值: SNAPSHOT, ALPHA, BETA, RC, st, 全部
     */
    private ReleaseType releaseType = ReleaseType.STABLE;

    /**
     * 允许执行的版本类型集合（用于更精细控制）
     * 当设置此属性时，会覆盖 release类型 的配置
     * 示例: plugin.datasource.script.allowed-release-类型=st,RC
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
     * 示例: plugin.datasource.script.initdata-超时-minutes=10
     */
    private int initdataTimeoutMinutes = 5;

    /**
     * 数据库初始化脚本的安装判断范围
     * <p>
     * table（默认）: 基于业务表判断 — 在 是否database空() 中排除已知系统表前缀（如 sys_），
     *               避免其他模块创建的系统表导致初始化脚本被跳过。
     * 文件: 基于表全量判断 — 保持原行为，仅在数据库完全为空时执行初始化脚本。
     * </p>
     * 默认值: table
     * 示例: plugin.datasource.script.scope=文件
     */
    private String scope = "table";

    /**
     * 空库判断时排除的表名前缀列表
     * 仅在 scope=table 时生效
     * 默认值: ["sys_"]
     * 示例: plugin.datasource.script.exclude-table-前缀=sys_,行为_
     */
    private String[] excludeTablePrefixes = {"sys_"};

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    /**
     * 是否enable。
     * @return 是否enable的结果
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 是否继续on错误。
     * @return 是否继续on错误的结果
     */
    public boolean isContinueOnError() {
        return continueOnError;
    }

    /**
     * 获取扫描mode。
     * @return 获取扫描mode的结果
     */
    public ScanMode getScanMode() {
        return scanMode;
    }

    /**
     * 获取effective扫描mode。
     * @return 获取effective扫描mode的结果
     */
    public ScanMode getEffectiveScanMode() {
        return enable ? scanMode : ScanMode.NONE;
    }

    /**
     * 获取模式扫描mode。
     * @return 获取模式扫描mode的结果
     */
    public ScanMode getSchemaScanMode() {
        return scanMode;
    }

    /**
     * 获取effective模式扫描mode。
     * @return 获取effective模式扫描mode的结果
     */
    public ScanMode getEffectiveSchemaScanMode() {
        return enable ? scanMode : ScanMode.NONE;
    }

    /**
     * 获取migration扫描mode。
     * @return 获取migration扫描mode的结果
     */
    public RepeatableScanMode getMigrationScanMode() {
        return migrationScanMode;
    }

    /**
     * 获取effectivemigration扫描mode。
     * @return 获取effectivemigration扫描mode的结果
     */
    public RepeatableScanMode getEffectiveMigrationScanMode() {
        return enable ? migrationScanMode : RepeatableScanMode.NONE;
    }

    /**
     * 获取数据扫描mode。
     * @return 获取数据扫描mode的结果
     */
    public ScanMode getDataScanMode() {
        return dataScanMode;
    }

    /**
     * 获取effective数据扫描mode。
     * @return 获取effective数据扫描mode的结果
     */
    public ScanMode getEffectiveDataScanMode() {
        return enable ? dataScanMode : ScanMode.NONE;
    }

    /**
     * 获取separator。
     * @return 获取separator的结果
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * 获取sqlscript编码。
     * @return 获取sqlscript编码的结果
     */
    public String getSqlScriptEncoding() {
        return sqlScriptEncoding;
    }

    /**
     * 获取script路径。
     * @return 获取script路径的结果
     */
    public String getScriptPath() {
        return scriptPath;
    }

    /**
     * 获取database类型。
     * @return 获取database类型的结果
     */
    public String getDatabaseType() {
        return databaseType;
    }

    /**
     * 是否baselineonmigrate。
     * @return 是否baselineonmigrate的结果
     */
    public boolean isBaselineOnMigrate() {
        return baselineOnMigrate;
    }

    /**
     * 是否clean已禁用。
     * @return 是否clean已禁用的结果
     */
    public boolean isCleanDisabled() {
        return cleanDisabled;
    }

    /**
     * 获取版本table。
     * @return 获取版本table的结果
     */
    public String getVersionTable() {
        return versionTable;
    }

    /**
     * 获取baseline版本。
     * @return 获取baseline版本的结果
     */
    public String getBaselineVersion() {
        return baselineVersion;
    }

    /**
     * 获取baselinedescription。
     * @return 获取baselinedescription的结果
     */
    public String getBaselineDescription() {
        return baselineDescription;
    }

    /**
     * 获取script前缀。
     * @return 获取script前缀的结果
     */
    public String getScriptPrefix() {
        return scriptPrefix;
    }

    /**
     * 获取script后缀。
     * @return 获取script后缀的结果
     */
    public String getScriptSuffix() {
        return scriptSuffix;
    }

    /**
     * 获取版本separator。
     * @return 获取版本separator的结果
     */
    public String getVersionSeparator() {
        return versionSeparator;
    }

    /**
     * 是否校验校验和。
     * @return 是否校验校验和的结果
     */
    public boolean isValidateChecksum() {
        return validateChecksum;
    }

    /**
     * 是否混合。
     * @return 是否混合的结果
     */
    public boolean isMixed() {
        return mixed;
    }

    /**
     * 获取超时。
     * @return 获取超时的结果
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * 是否verbose。
     * @return 是否verbose的结果
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * 获取release类型。
     * @return 获取release类型的结果
     */
    public ReleaseType getReleaseType() {
        return releaseType;
    }

    /**
     * 获取allowedrelease类型。
     * @return 获取allowedrelease类型的结果
     */
    public Set<ReleaseType> getAllowedReleaseTypes() {
        return allowedReleaseTypes;
    }

    /**
     * 获取initdataparallelism。
     * @return 获取initdataparallelism的结果
     */
    public int getInitdataParallelism() {
        return initdataParallelism;
    }

    /**
     * 获取initdata超时minutes。
     * @return 获取initdata超时minutes的结果
     */
    public int getInitdataTimeoutMinutes() {
        return initdataTimeoutMinutes;
    }

    /**
     * 获取scope。
     * @return 获取scope的结果
     */
    public String getScope() {
        return scope;
    }

    /**
     * 获取excludetable前缀。
     * @return 获取excludetable前缀的结果
     */
    public String[] getExcludeTablePrefixes() {
        return excludeTablePrefixes;
    }

    /**
     * 版本发布类型枚举
     * 用于控制允许执行的脚本版本类型
     *
     * @since 2025/12/4
     * @author CH
     */
    @Getter
    public enum ReleaseType {
        
        /**
         * 快照版本
         * 示例: V1.0.0-SNAPSHOT__初始化_用户.SQL
         * @param "snapshot" "snapshot"
         * @param 0 0
         * @return SNAPSHOT的结果
         */
        SNAPSHOT("snapshot", 0),
        
        /**
         * Alpha 内部测试版本
         * 示例: V1.0.0-alpha__初始化_用户.SQL
         * @param "alpha" "alpha"
         * @param 1 1
         * @return ALPHA的结果
         */
        ALPHA("alpha", 1),
        
        /**
         * Beta 公开测试版本
         * 示例: V1.0.0-beta__初始化_用户.SQL
         * @param "beta" "beta"
         * @param 2 2
         * @return BETA的结果
         */
        BETA("beta", 2),
        
        /**
         * RC（Release Candidate）候选发布版本
         * 示例: V1.0.0-rc1__初始化_用户.SQL
         * @param "rc" "rc"
         * @param 3 3
         * @return RC的结果
         */
        RC("rc", 3),
        
        /**
         * 正式稳定版本
         * 示例: V1.0.0__初始化_用户.SQL
         * @param "stable" "stable"
         * @param 4 4
         * @return st的结果
         */
        STABLE("stable", 4),
        
        /**
         * 允许所有版本类型
         * @param "all" "全部"
         * @param 99 99
         * @return 全部的结果
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
         * 构造器
         *
         * @param suffix 版本类型后缀标识
         * @param priority 版本类型优先级
         */
        ReleaseType(String suffix, int priority) {
            this.suffix = suffix;
            this.priority = priority;
        }

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

    /**
     * 数据库脚本扫描模式
     * @author CH
     * @since 4.0.0
     */
    public enum ScanMode {
        /**
         * 不扫描
         */
        NONE,
        /**
         * 仅当当前数据库无表时扫描一次
         */
        ONCE,
        /**
         * 每次启动都扫描
         */
        ALWAYS
    }

    /**
     * 可重复执行脚本扫描模式
     * @author CH
     * @since 4.0.0
     */
    public enum RepeatableScanMode {
        /**
         * 不扫描
         */
        NONE,
        /**
         * 每次启动都扫描
         */
        ALWAYS
    }
}
