package com.chua.starter.job.support.log;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.entity.SysJobLog;
import com.chua.starter.job.support.entity.SysJobLogBackup;
import com.chua.starter.job.support.entity.SysJobLogDetail;
import com.chua.starter.job.support.mapper.SysJobLogBackupMapper;
import com.chua.starter.job.support.mapper.SysJobLogDetailMapper;
import com.chua.starter.job.support.mapper.SysJobLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 任务日志备份服务
 * <p>
 * 提供日志压缩备份和定时清理功能
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogBackupService {

    private final JobProperties jobProperties;
    private final SysJobLogMapper jobLogMapper;
    private final SysJobLogDetailMapper jobLogDetailMapper;
    private final SysJobLogBackupMapper jobLogBackupMapper;

    private final AtomicBoolean isBackupRunning = new AtomicBoolean(false);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 手动触发备份
     *
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @param compressType 压缩类型: ZIP/GZIP
     * @param cleanAfter   备份后是否清理原始日志
     * @return 备份记录
     */
    public SysJobLogBackup startBackup(LocalDate startDate, LocalDate endDate, 
                                            String compressType, boolean cleanAfter) {
        if (!isBackupRunning.compareAndSet(false, true)) {
            log.warn("备份任务正在运行中，请稍后再试");
            return null;
        }

        SysJobLogBackup backup = new SysJobLogBackup();
        backup.setJobLogBackupStartDate(startDate);
        backup.setJobLogBackupEndDate(endDate);
        backup.setJobLogBackupStatus("RUNNING");
        backup.setJobLogBackupType("MANUAL");
        backup.setJobLogBackupCompressType(compressType);
        backup.setJobLogBackupStartTime(LocalDateTime.now());
        backup.setJobLogBackupCleaned(0);
        backup.setCreateTime(LocalDateTime.now());
        jobLogBackupMapper.insert(backup);

        try {
            doBackup(backup, cleanAfter);
        } finally {
            isBackupRunning.set(false);
        }

        return backup;
    }

    /**
     * 定时自动备份任务
     * 每天凌晨3点执行，备份30天前的日志
     */
    @Scheduled(cron = "${plugin.job.auto-backup-cron:0 0 3 * * ?}")
    public void autoBackup() {
        // 检查是否启用自动备份
        if (!jobProperties.isAutoBackupEnabled()) {
            return;
        }

        int retentionDays = jobProperties.getLogRetentionDays();
        if (retentionDays <= 0) {
            return;
        }

        if (!isBackupRunning.compareAndSet(false, true)) {
            log.warn("备份任务正在运行中，跳过本次自动备份");
            return;
        }

        try {
            LocalDate endDate = LocalDate.now().minusDays(retentionDays);
            LocalDate startDate = endDate.minusDays(7); // 每次备份7天的数据

            SysJobLogBackup backup = new SysJobLogBackup();
            backup.setJobLogBackupStartDate(startDate);
            backup.setJobLogBackupEndDate(endDate);
            backup.setJobLogBackupStatus("RUNNING");
            backup.setJobLogBackupType("AUTO");
            backup.setJobLogBackupCompressType("ZIP");
            backup.setJobLogBackupStartTime(LocalDateTime.now());
            backup.setJobLogBackupCleaned(0);
            backup.setCreateTime(LocalDateTime.now());
            jobLogBackupMapper.insert(backup);

            doBackup(backup, true); // 自动备份后清理
        } finally {
            isBackupRunning.set(false);
        }
    }

    /**
     * 执行备份
     */
    private void doBackup(SysJobLogBackup backup, boolean cleanAfter) {
        long startTime = System.currentTimeMillis();
        String backupPath = getBackupPath();

        try {
            // 查询日志
            LocalDate startDate = backup.getJobLogBackupStartDate();
            LocalDate endDate = backup.getJobLogBackupEndDate();

            List<SysJobLog> logs = jobLogMapper.selectList(
                    Wrappers.<SysJobLog>lambdaQuery()
                            .between(SysJobLog::getJobLogTriggerDate, startDate, endDate)
            );

            if (logs == null || logs.isEmpty()) {
                backup.setJobLogBackupStatus("SUCCESS");
                backup.setJobLogBackupCount(0L);
                backup.setJobLogBackupMessage("没有需要备份的日志");
                backup.setJobLogBackupEndTime(LocalDateTime.now());
                backup.setJobLogBackupCost(System.currentTimeMillis() - startTime);
                jobLogBackupMapper.updateById(backup);
                log.info("没有需要备份的日志: {} ~ {}", startDate, endDate);
                return;
            }

            // 生成备份文件名
            String fileName = String.format("job_log_backup_%s_%s.%s",
                    startDate.format(DATE_FORMATTER),
                    endDate.format(DATE_FORMATTER),
                    "ZIP".equals(backup.getJobLogBackupCompressType()) ? "zip" : "gz");
            String filePath = backupPath + File.separator + fileName;

            // 创建备份目录
            Files.createDirectories(Paths.get(backupPath));

            // 压缩备份
            long fileSize;
            if ("ZIP".equals(backup.getJobLogBackupCompressType())) {
                fileSize = createZipBackup(filePath, logs, startDate, endDate);
            } else {
                fileSize = createGzipBackup(filePath, logs);
            }

            // 更新备份记录
            backup.setJobLogBackupFileName(fileName);
            backup.setJobLogBackupFilePath(filePath);
            backup.setJobLogBackupFileSize(fileSize);
            backup.setJobLogBackupCount((long) logs.size());
            backup.setJobLogBackupStatus("SUCCESS");
            backup.setJobLogBackupEndTime(LocalDateTime.now());
            backup.setJobLogBackupCost(System.currentTimeMillis() - startTime);
            backup.setJobLogBackupMessage("备份成功");

            // 清理原始日志
            if (cleanAfter) {
                int deleted = jobLogMapper.delete(
                        Wrappers.<SysJobLog>lambdaQuery()
                                .between(SysJobLog::getJobLogTriggerDate, startDate, endDate)
                );
                // 清理日志详情
                LocalDateTime startDateTime = startDate.atStartOfDay();
                LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
                jobLogDetailMapper.deleteBeforeTime(endDateTime);
                
                backup.setJobLogBackupCleaned(1);
                log.info("已清理 {} 条原始日志", deleted);
            }

            jobLogBackupMapper.updateById(backup);
            log.info("日志备份完成: {}, 共 {} 条, 文件大小: {} bytes", 
                    fileName, logs.size(), fileSize);

        } catch (Exception e) {
            log.error("备份失败", e);
            backup.setJobLogBackupStatus("FAILED");
            backup.setJobLogBackupEndTime(LocalDateTime.now());
            backup.setJobLogBackupCost(System.currentTimeMillis() - startTime);
            backup.setJobLogBackupMessage("备份失败: " + e.getMessage());
            jobLogBackupMapper.updateById(backup);
        }
    }

    /**
     * 创建 ZIP 备份
     */
    private long createZipBackup(String filePath, List<SysJobLog> logs,
                                  LocalDate startDate, LocalDate endDate) throws IOException {
        File file = new File(filePath);
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8)) {

            // 写入日志主表数据
            ZipEntry logEntry = new ZipEntry("job_logs.json");
            zos.putNextEntry(logEntry);
            zos.write(Json.toJson(logs).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 写入日志详情
            for (SysJobLog log : logs) {
                List<SysJobLogDetail> details = jobLogDetailMapper.selectByJobLogId(log.getJobLogId());
                if (details != null && !details.isEmpty()) {
                    ZipEntry detailEntry = new ZipEntry("details/job_log_" + log.getJobLogId() + ".json");
                    zos.putNextEntry(detailEntry);
                    zos.write(Json.toJson(details).getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }

            // 写入日志文件（如果存在）
            String logBasePath = JobFileAppender.getLogPath();
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                String datePath = current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                File dateDir = new File(logBasePath, datePath);
                if (dateDir.exists() && dateDir.isDirectory()) {
                    File[] logFiles = dateDir.listFiles((dir, name) -> name.endsWith(".log"));
                    if (logFiles != null) {
                        for (File logFile : logFiles) {
                            ZipEntry fileEntry = new ZipEntry("files/" + datePath + "/" + logFile.getName());
                            zos.putNextEntry(fileEntry);
                            Files.copy(logFile.toPath(), zos);
                            zos.closeEntry();
                        }
                    }
                }
                current = current.plusDays(1);
            }
        }
        return file.length();
    }

    /**
     * 创建 GZIP 备份
     */
    private long createGzipBackup(String filePath, List<SysJobLog> logs) throws IOException {
        File file = new File(filePath);
        try (GZIPOutputStream gzos = new GZIPOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            gzos.write(Json.toJson(logs).getBytes(StandardCharsets.UTF_8));
        }
        return file.length();
    }

    /**
     * 获取备份存储路径
     */
    private String getBackupPath() {
        String logPath = jobProperties.getLogPath();
        return logPath + File.separator + "backup";
    }

    /**
     * 查询备份状态
     */
    public boolean isBackupRunning() {
        return isBackupRunning.get();
    }

    /**
     * 获取最近的备份记录
     */
    public List<SysJobLogBackup> getRecentBackups(int limit) {
        return jobLogBackupMapper.selectRecent(limit);
    }

    /**
     * 删除备份
     */
    public boolean deleteBackup(Long backupId) {
        SysJobLogBackup backup = jobLogBackupMapper.selectById(backupId);
        if (backup == null) {
            return false;
        }

        // 删除文件
        if (backup.getJobLogBackupFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(backup.getJobLogBackupFilePath()));
            } catch (IOException e) {
                log.error("删除备份文件失败: {}", backup.getJobLogBackupFilePath(), e);
            }
        }

        // 删除记录
        jobLogBackupMapper.deleteById(backupId);
        return true;
    }

    /**
     * 下载备份文件
     */
    public byte[] downloadBackup(Long backupId) throws IOException {
        SysJobLogBackup backup = jobLogBackupMapper.selectById(backupId);
        if (backup == null || backup.getJobLogBackupFilePath() == null) {
            return null;
        }

        Path path = Paths.get(backup.getJobLogBackupFilePath());
        if (!Files.exists(path)) {
            return null;
        }

        return Files.readAllBytes(path);
    }

    /**
     * 清理过期备份
     *
     * @param retentionDays 保留天数
     * @return 清理数量
     */
    public int cleanExpiredBackups(int retentionDays) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);
        List<SysJobLogBackup> expiredBackups = jobLogBackupMapper.selectList(
                Wrappers.<SysJobLogBackup>lambdaQuery()
                        .lt(SysJobLogBackup::getCreateTime, expireTime)
        );

        int count = 0;
        for (SysJobLogBackup backup : expiredBackups) {
            if (deleteBackup(backup.getJobLogBackupId())) {
                count++;
            }
        }

        log.info("清理过期备份: {} 个", count);
        return count;
    }
}
