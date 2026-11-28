package com.chua.starter.datapool.support.backup;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.task.backup.BackupHistoryRecord;
import com.chua.common.support.task.backup.BackupHistoryStore;
import com.chua.starter.datapool.support.entity.MsBackupHistory;
import com.chua.starter.datapool.support.mapper.MsBackupHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据库备份历史的数据库存储实现。
 *
 * <p>utils 层 {@link BackupHistoryStore} SPI 的 datapool 侧落地方案：把每次备份的
 * 审计记录写入 {@code ms_backup_history} 表，替代默认的 JSONL 文件存储，便于
 * 备份中心页面按数据库查询、多实例共享。接口契约：{@link #append} 与
 * {@link #list} 均需线程安全；存储故障只记录告警，不允许中断备份流程。</p>
 *
 * @author CH
 * @since 2026/11/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbBackupHistoryStore implements BackupHistoryStore {

    private final MsBackupHistoryMapper backupHistoryMapper;

    /**
     * 追加一条备份历史记录（成功与失败都记录）。
     *
     * <p>落库失败只告警不抛出：历史记录是审计增强，不能因它中断备份主链路。</p>
     *
     * @param record 备份历史记录
     */
    @Override
    public void append(BackupHistoryRecord record) {
        if (record == null) {
            return;
        }
        try {
            backupHistoryMapper.insert(toEntity(record));
        } catch (Exception e) {
            log.warn("[database-backup] 备份历史落库失败: {}", e.getMessage());
        }
    }

    /**
     * 查询最近的备份历史（按落库时间倒序）。
     *
     * @param limit 最多返回条数（&le;0 时返回空列表）
     * @return 历史记录列表
     */
    @Override
    public List<BackupHistoryRecord> list(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        try {
            List<MsBackupHistory> rows = backupHistoryMapper.selectList(
                    Wrappers.<MsBackupHistory>lambdaQuery()
                            .orderByDesc(MsBackupHistory::getCreateTime)
                            .orderByDesc(MsBackupHistory::getId)
                            .last("LIMIT " + limit));
            return rows.stream().map(DbBackupHistoryStore::toRecord).toList();
        } catch (Exception e) {
            log.warn("[database-backup] 备份历史查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 审计记录转实体。
     *
     * @param record utils 层审计记录
     * @return 实体
     */
    private static MsBackupHistory toEntity(BackupHistoryRecord record) {
        MsBackupHistory entity = new MsBackupHistory();
        entity.setRecordId(record.id());
        entity.setDbName(record.dbName());
        entity.setSuccess(record.success() ? 1 : 0);
        entity.setBackupPath(record.backupPath());
        entity.setManifestPath(record.manifestPath());
        entity.setFileCount(record.fileCount());
        entity.setTotalSizeBytes(record.totalSizeBytes());
        entity.setDurationMillis(record.durationMillis());
        entity.setStartTime(record.startTime());
        entity.setEndTime(record.endTime());
        entity.setErrorMessage(record.errorMessage());
        return entity;
    }

    /**
     * 实体转 utils 层审计记录。
     *
     * @param entity 实体
     * @return utils 层审计记录
     */
    private static BackupHistoryRecord toRecord(MsBackupHistory entity) {
        boolean success = entity.getSuccess() != null && entity.getSuccess() == 1;
        return new BackupHistoryRecord(
                entity.getRecordId(),
                entity.getDbName(),
                success,
                entity.getBackupPath(),
                entity.getManifestPath(),
                entity.getFileCount() == null ? 0 : entity.getFileCount(),
                entity.getTotalSizeBytes() == null ? 0L : entity.getTotalSizeBytes(),
                entity.getDurationMillis() == null ? 0L : entity.getDurationMillis(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getErrorMessage());
    }
}
