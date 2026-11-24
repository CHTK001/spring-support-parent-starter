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
 * <p>配置示例：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     database-backup:
 *       enabled: true
 *       backup-dir: D:/backup/database
 *       retention-days: 30
 *       max-backup-count: 0
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
     * 是否启用备份中心（关闭后 Controller 返回 404 由前端提示未装配）。
     */
    private boolean enabled = true;

    /**
     * 备份根目录；为空时默认 {user.home}/monitor-backup/database。
     */
    private String backupDir;

    /**
     * 备份保留天数：超过则清理历史备份（utils 策略默认 30 天）。
     */
    private int retentionDays = 30;

    /**
     * 最大备份份数：按 database-* 脚本计数，超出后从最旧开始清理；
     * 0 表示不按份数清理。
     */
    private int maxBackupCount = 0;

    /**
     * 一致性快照导出：备份期间用单连接事务包住全表导出，保证多表数据取自同一快照。
     */
    private boolean consistentSnapshot = true;

    /**
     * 是否在备份目录写入 manifest.json（记录每个脚本的字节数与 SHA-256 校验和），
     * 供恢复前校验。
     */
    private boolean writeManifest = true;
}
