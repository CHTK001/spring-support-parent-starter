package com.chua.starter.datasourcehub.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据源中心配置属性。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "datasource.hub")
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
}