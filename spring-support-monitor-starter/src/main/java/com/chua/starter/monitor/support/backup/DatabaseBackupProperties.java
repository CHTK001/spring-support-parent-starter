package com.chua.starter.monitor.support.backup;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据库备份中心配置。
 *
 * <p>控制备份产物落点与保留策略；数据库连接信息不从这里配置，
 * 而是从宿主当前 Spring DataSource 解析，保证备份的「当前库」与业务库一致。</p>
 *
 * <p>完整配置示例（前缀 {@code plugin.monitor.database-backup}，值均为默认值）：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     database-backup:
 *       enabled: true                  # 关闭后备份接口返回「备份中心未启用」业务错误码
 *       backup-dir: ""                 # 备份根目录；空回退 {user.home}/monitor-backup/database
 *       retention-days: 30             # 备份保留天数
 *       max-backup-count: 0            # 最大备份份数；0 表示不按份数清理
 *       consistent-snapshot: true      # 单连接事务一致性快照导出
 *       write-manifest: true           # 写 manifest.json（字节数 + SHA-256）
 * </pre>
 *
 * @author CH
 * @since 2026/11/24
 */
@Data
@Component
@ConfigurationProperties(prefix = "plugin.monitor.database-backup")
public class DatabaseBackupProperties {

    /**
     * 是否启用备份中心。
     *
     * <p>默认值：{@code true}；关闭后 Controller 返回 HTTP 200 与业务错误码「备份中心未启用」，由前端提示未装配。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     database-backup:
     *       enabled: true
     * </pre>
     */
    private boolean enabled = true;

    /**
     * 备份根目录。
     *
     * <p>默认值：空（{@code null}），运行时由 {@code DatabaseBackupService#backupDir()}
     * 回退到 {@code {user.home}/monitor-backup/database}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     database-backup:
     *       backup-dir: ""
     * </pre>
     */
    private String backupDir;

    /**
     * 备份保留天数：超过则清理历史备份。
     *
     * <p>默认值：{@code 30}（utils 备份策略默认值）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     database-backup:
     *       retention-days: 30
     * </pre>
     */
    private int retentionDays = 30;

    /**
     * 最大备份份数：按 {@code database-*} 脚本计数，超出后从最旧开始清理。
     *
     * <p>默认值：{@code 0}，表示不按份数清理（仅按 {@code retention-days} 过期清理）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     database-backup:
     *       max-backup-count: 0
     * </pre>
     */
    private int maxBackupCount = 0;

    /**
     * 一致性快照导出：备份期间用单连接事务包住全表导出，保证多表数据取自同一快照。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     database-backup:
     *       consistent-snapshot: true
     * </pre>
     */
    private boolean consistentSnapshot = true;

    /**
     * 是否在备份目录写入 {@code manifest.json}
     * （记录每个脚本的字节数与 SHA-256 校验和），供恢复前校验。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     database-backup:
     *       write-manifest: true
     * </pre>
     */
    private boolean writeManifest = true;
}
