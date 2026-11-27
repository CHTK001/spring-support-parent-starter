package com.chua.starter.datapool.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源中心配置属性。
 *
 * <p>配置前缀 {@code plugin.datapool}，是本模块唯一的 {@code @ConfigurationProperties}，
 * 由 {@link DatapoolAutoConfiguration} 经 {@code @EnableConfigurationProperties} 无条件装配
 * （relaxed binding，推荐连字符风格，如 {@code connection-ttl-minutes}）。</p>
 *
 * <p>完整 YAML 配置示例（各项含义与回落值见对应字段注释）：</p>
 *
 * <pre>
 * plugin:
 *   datapool:
 *     enabled: true                        # 服务信息面板展示旗标（非模块开关），默认 true
 *     connection-ttl-minutes: 10           # JDBC 连接空闲存活分钟数；≤0 按 1 处理
 *     connection-sweep-interval-ms: 60000  # 空闲连接清扫周期，正数毫秒
 *     probe-timeout-ms: 5000               # 连通性探测超时（毫秒）
 *     connect-timeout-seconds: 30          # JDBC 建连超时（秒）；≤0 按 1 处理
 *     query-timeout-seconds: 60            # SQL 执行超时（秒）；0 = 不设上限
 *     driver-jar-dir: data/jdbc-driver     # 驱动 jar 落盘目录（默认在工作目录下，按平台分隔符）
 *     driver-jar-max-bytes: 67108864       # 驱动 jar 单个上限（字节），默认 64MB
 *     file-db-upload-dir: data/file-db     # 文件库上传目录（默认在工作目录下，按平台分隔符）
 *     file-db-max-bytes: 67108864          # 文件库单个上限（字节），默认 64MB
 *     file-jdbc-allowed-dirs:              # 文件库路径白名单；留空 = 工作目录/临时目录/用户主目录
 *       - /data/dbs                        # 追加放行的根目录示例
 *     secret-key: ${DATASOURCE_SECRET_KEY:change-me}  # 口令加密密钥；留空 = 明文入库；配了不要换
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.datapool")
public class DatapoolProperties {

    /**
     * 服务信息面板上「数据源中心」模块的展示旗标（非模块开关）。
     *
     * <p>默认值：{@code true}。注意这不是模块开关：本模块装配无条件，
     * 置为 {@code false} 只会隐藏服务信息面板上的「数据源中心」旗标，
     * 不会关闭数据源中心的任何能力。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     enabled: true
     * </pre>
     */
    private boolean enabled = true;

    /**
     * JDBC 连接空闲存活时长（分钟），超时自动关闭（小于 1 按 1 处理）。
     *
     * <p>默认值：{@code 10}；配置 ≤0 时按 {@code 1} 处理（取用侧 {@code Math.max(1L, ...)}）。
     * 过期为惰性触发 + 后台清扫（见 {@code connectionSweepIntervalMs}），
     * TTL 内按独占借还复用物理连接；借出的连接不会被超时清扫关闭。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     connection-ttl-minutes: 10
     * </pre>
     */
    // [P3C A20 豁免]
    private long connectionTtlMinutes = 10L;

    /**
     * 空闲连接后台清扫周期（毫秒），默认 60 秒。
     *
     * <p>后台任务调用底层连接池的空闲淘汰入口；即使没有后续请求，也会关闭超过
     * TTL 的空闲物理连接。正在借用的连接不参与淘汰。</p>
     *
     * <p>默认值：{@code 60000}（60 秒），通过调度注解读取配置，须为正数。
     * 修改配置后在下次应用启动时生效。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     connection-sweep-interval-ms: 60000
     * </pre>
     */
    private long connectionSweepIntervalMs = 60_000L;

    /**
     * 连通性探测超时（毫秒）。
     *
     * <p>默认值：{@code 5000}（5 秒）。台账连通性测试与指标采集探活共用该超时；
     * 弱网/跨机房目标可适当调大。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     probe-timeout-ms: 5000
     * </pre>
     */
    // [P3C A20 豁免]
    private int probeTimeoutMs = 5000;

    /**
     * JDBC 建连超时（秒），默认 30。
     *
     * <p>必须有上限：不少驱动默认**不**设建连超时，主机不可达时会挂在
     * TCP 层等系统级超时（可到两分钟以上），界面上表现为「一直转圈」，
     * 而用户真正想改的往往就是这一个数。</p>
     *
     * <p>实现按驱动族换算单位：MySQL/MariaDB 与 Oracle 的 {@code connectTimeout}
     * 是**毫秒**，PostgreSQL 的是**秒**，SQL Server 只认 {@code loginTimeout}（秒）。
     * 写错单位会让「30 秒」变成 30 毫秒（必超时）或 30 万秒（等于没设）。</p>
     *
     * <p>默认值：{@code 30}；配置 ≤0 时按 {@code 1} 秒处理（取用侧 {@code Math.max(1, ...)}）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     connect-timeout-seconds: 30
     * </pre>
     */
    // [P3C A20 豁免]
    private int connectTimeoutSeconds = 30;

    /**
     * SQL 执行超时（秒），0 = 不设上限（沿用驱动/数据库默认）。
     *
     * <p>对应 JDBC {@code Statement.setQueryTimeout}：一条失控的 SQL（大表全表扫描、
     * 行锁/表锁等待、笛卡尔积）默认会让请求**无限期**挂在数据库侧 —— 前端表现为
     * 「执行按钮一直转圈直到网关超时」（用户实测：{@code timeout of 10000ms exceeded}），
     * 既看不到错误也取消不了。设成有界值后驱动抛 {@code SQLTimeoutException}，
     * 接口正常失败并把原因带回界面。</p>
     *
     * <p>只约束语句级执行（查询/批量/单语句事务），不影响连接本身的建连超时；
     * 按库的慢查询水位调整，0 关闭。</p>
     *
     * <p>默认值：{@code 60}；配置 ≤0（含 0）时<b>不设上限</b>——仅当大于 0 才会调用
     * {@code Statement.setQueryTimeout}，沿用驱动/数据库默认。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     query-timeout-seconds: 60
     * </pre>
     */
    // [P3C A20 豁免]
    private int queryTimeoutSeconds = 60;

    /**
     * 上传的 JDBC 驱动 jar 落盘目录。
     *
     * <p>默认放在应用工作目录下的 {@code data/jdbc-driver}，而不是临时目录：
     * 临时目录会被系统清理任务扫掉，用户某天重启就发现数据源连不上了，
     * 而台账里还记着这个 jar。</p>
     *
     * <p>文件名落盘时统一加内容指纹，避免同名不同内容的 jar 互相覆盖。</p>
     *
     * <p>默认值：{@code data/jdbc-driver}（按 {@code File.separator} 拼接，
     * 即 Windows 下为 {@code data\jdbc-driver}；路径相对应用工作目录）。
     * 自定义时建议仍指向持久磁盘上的专用目录，不要放会被系统清理的临时目录。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     driver-jar-dir: data/jdbc-driver
     * </pre>
     */
    private String driverJarDir = "data" + java.io.File.separator + "jdbc-driver";

    /**
     * 驱动 jar 单个大小上限（字节），默认 64MB。
     *
     * <p>JDBC 驱动正常在 100KB~10MB；给一个明显越界的上限是为了防止
     * 「误传了整个 JDK」或「传了源码包」把磁盘写满 —— 这个目录在生产机上，
     * 而后台又是常驻的。</p>
     *
     * <p>默认值：{@code 67108864}（64MB）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     driver-jar-max-bytes: 67108864
     * </pre>
     */
    // [P3C A20 豁免]
    private long driverJarMaxBytes = 64L * 1024 * 1024;

    /**
     * 文件库（SQLite / DuckDB / H2）库文件的上传落盘目录。
     *
     * <p>默认 {@code data/file-db}，落在应用工作目录下：这类库要的是
     * 「服务器上一个可写的绝对路径」，放进临时目录会被系统清理任务扫掉，
     * 台账里还留着那个路径，第二天就连不上了。</p>
     *
     * <p>该目录应落在 {@code fileJdbcAllowedDirs} 允许的范围内，
     * 否则建连会被路径白名单挡下 —— 默认值本身就在工作目录内，无需额外配置。</p>
     *
     * <p>默认值：{@code data/file-db}（按 {@code File.separator} 拼接，路径相对应用工作目录）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     file-db-upload-dir: data/file-db
     * </pre>
     */
    private String fileDbUploadDir = "data" + java.io.File.separator + "file-db";

    /**
     * 文件库库文件的单个大小上限（字节），默认 64MB。
     *
     * <p>单文件库正常在几 MB 以内；给上限是为了防止误传一个几百 MB 的
     * 数据仓库文件把磁盘写满。要传更大的库，把这个值调大即可。</p>
     *
     * <p>默认值：{@code 67108864}（64MB）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     file-db-max-bytes: 67108864
     * </pre>
     */
    // [P3C A20 豁免]
    private long fileDbMaxBytes = 64L * 1024 * 1024;

    /**
     * 文件库（SQLite / H2 file / Derby / HSQLDB）JDBC URL 允许的根目录白名单。
     *
     * <p>这类连接串里带的是**磁盘路径**，而连接串来自数据源台账 ——
     * 台账里出现 {@code ../../etc/...} 这类穿越路径时，探活与 SQL 执行会去
     * 读写任意文件。所以解析后必须落在白名单目录内才允许建连。</p>
     *
     * <p>留空时默认放行三个目录：<b>工作目录</b>（user.dir）、<b>系统临时目录</b>
     * （java.io.tmpdir）、<b>用户主目录</b>（user.home）—— 分别覆盖「相对路径落盘」、
     * 「/tmp 试用一把」、「{@code jdbc:h2:~/data}」三种正常用法；
     * 要放行别的目录（如 /data/dbs）在此显式追加。</p>
     *
     * <p>默认值：空列表（回落放行工作目录 / 系统临时目录 / 用户主目录三处）。</p>
     *
     * <p>YAML 示例（列表项为根目录，解析后的库文件路径落在其下即放行）：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     file-jdbc-allowed-dirs:
     *       - /data/dbs
     *       - D:/app-data/file-db
     * </pre>
     */
    private List<String> fileJdbcAllowedDirs = new ArrayList<>();

    /**
     * 数据源口令的加密密钥（原文，内部经 SHA-256 派生 AES-256 密钥）。
     *
     * <p>留空表示<b>不加密</b>，口令按旧行为明文入库 —— 这是刻意选择：
     * 用一个内置默认密钥「假装加密」比明文更糟，它会让人以为口令已经安全了。</p>
     *
     * <p>配置后只影响<b>之后写入</b>的口令；台账里已有的明文与 {@code b64:} 值
     * 仍能正常读出（按前缀分支解码），下次保存该数据源时自动转成密文。</p>
     *
     * <p><strong>配了就不要换。</strong> 更换密钥会让此前写下的密文全部解不开，
     * 那些数据源的口令只能重新录入。密钥放配置文件或环境变量，不要进代码库。</p>
     *
     * <p>默认值：空串（不加密，口令明文入库，进程启动时打一次 WARN）。</p>
     *
     * <p>YAML 示例（推荐经环境变量注入，避免明文进配置仓库）：</p>
     * <pre>
     * plugin:
     *   datapool:
     *     secret-key: ${DATASOURCE_SECRET_KEY:change-me}
     * </pre>
     */
    private String secretKey = "";

    /**
     * 把密钥原文交给 {@code DatasourceSecrets}。
     *
     * <p>口令编解码是静态工具（服务层、连接构建处都要用，且不都走 Spring 注入），
     * 这里在配置绑定完成后推一次，避免每个调用点都去拿 Environment。</p>
     */
    @jakarta.annotation.PostConstruct
    void applySecretKey() {
        com.chua.starter.datapool.support.support.DatasourceSecrets.setSecretKey(secretKey);
    }
}
