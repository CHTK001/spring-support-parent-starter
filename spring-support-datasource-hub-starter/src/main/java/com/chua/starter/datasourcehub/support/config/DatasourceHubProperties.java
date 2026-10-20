package com.chua.starter.datasourcehub.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源中心配置属性。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.datasource.hub")
public class DatasourceHubProperties {

    /**
     * 服务信息面板上「数据源中心」模块的展示旗标（非模块开关）
     */
    private boolean enabled = true;

    /**
     * JDBC 连接空闲存活时长（分钟），超时自动关闭（小于 1 按 1 处理）
     */
    private long connectionTtlMinutes = 10L;

    /**
     * 空闲连接后台清扫周期（毫秒），默认 60 秒，0 = 关闭清扫。
     *
     * <p>{@link ExpireValue} 的过期是<b>惰性</b>的 —— 只有下一次取连接时才会触发
     * 过期回调去关闭物理连接；数据源一旦闲置（页面关了、当天不再查询），那条连接
     * 会一直挂到进程重启或数据源被删除。这里给后台一个定期主动核对的节拍：
     * 到期的连接由清扫任务触发同一套过期回调关闭，既不泄漏 socket，
     * 也不影响 TTL 内的复用（正在使用的连接刚刷新过、不会处于过期态）。</p>
     */
    private long connectionSweepIntervalMs = 60_000L;

    /**
     * 连通性探测超时（毫秒）
     */
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
     */
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
     */
    private int queryTimeoutSeconds = 60;

    /**
     * 上传的 JDBC 驱动 jar 落盘目录。
     *
     * <p>默认放在应用工作目录下的 {@code data/jdbc-driver}，而不是临时目录：
     * 临时目录会被系统清理任务扫掉，用户某天重启就发现数据源连不上了，
     * 而台账里还记着这个 jar。</p>
     *
     * <p>文件名落盘时统一加内容指纹，避免同名不同内容的 jar 互相覆盖。</p>
     */
    private String driverJarDir = "data" + java.io.File.separator + "jdbc-driver";

    /**
     * 驱动 jar 单个大小上限（字节），默认 64MB。
     *
     * <p>JDBC 驱动正常在 100KB~10MB；给一个明显越界的上限是为了防止
     * 「误传了整个 JDK」或「传了源码包」把磁盘写满 —— 这个目录在生产机上，
     * 而后台又是常驻的。</p>
     */
    private long driverJarMaxBytes = 64L * 1024 * 1024;

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
     */
    private List<String> fileJdbcAllowedDirs = new ArrayList<>();
}