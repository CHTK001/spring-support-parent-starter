package com.chua.starter.filesystem.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.mapper.SysFileSystemUploadMapper;
import com.chua.starter.filesystem.support.properties.FileStorageUploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文件系统上传数据清理调度服务。
 *
 * <p>定期清理超过保留天数的终态任务（完成/失败/取消），级联删除
 * 分片表、文件主体表、合并记录表、上传记录表及磁盘临时文件。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSystemCleanupScheduler
        extends ServiceImpl<SysFileSystemUploadMapper, SysFileSystemUpload> {

    /**
     * 终态状态集合：完成/失败/取消
     */
    private static final List<Integer> TERMINAL_STATUS = List.of(
            SysFileSystemUpload.Status.COMPLETED,
            SysFileSystemUpload.Status.FAILED,
            SysFileSystemUpload.Status.CANCELLED);

    /**
     * 上传任务服务
     */
    private final SysFileSystemUploadService uploadService;

    /**
     * 分片详情服务
     */
    private final SysFileSystemUploadChunkService chunkService;

    /**
     * 文件主体服务
     */
    private final SysFileSystemUploadFileService fileService;

    /**
     * 合并记录服务
     */
    private final SysFileSystemUploadMergeLogService mergeLogService;

    /**
     * 上传记录服务
     */
    private final SysFileSystemUploadUploadLogService uploadLogService;

    /**
     * 上传配置
     */
    private final FileStorageUploadProperties uploadProperties;

    /**
     * 定期清理过期终态任务。
     */
    @Scheduled(fixedDelayString = "${plugin.filesystem.upload.cleanup-interval-ms:86400000}")
    public void cleanExpiredTasks() {
        int retentionDays = uploadProperties.getRetentionDays();
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(retentionDays);
        log.info("开始清理 {} 天前的终态任务...", retentionDays);

        List<SysFileSystemUpload> expiredTasks = list(new LambdaQueryWrapper<SysFileSystemUpload>()
                .in(SysFileSystemUpload::getFilesystemUploadTaskStatus, TERMINAL_STATUS)
                .lt(SysFileSystemUpload::getFilesystemUploadTaskCompleteTime, beforeTime));

        int count = 0;
        for (SysFileSystemUpload task : expiredTasks) {
            String uploadId = task.getFilesystemUploadTaskUploadId();
            try {
                // 级联删除业务表数据
                chunkService.deleteByUploadId(uploadId);
                fileService.deleteByUploadId(uploadId);
                mergeLogService.deleteByUploadId(uploadId);
                uploadLogService.deleteByUploadId(uploadId);
                // 清理磁盘临时文件（分片 + 合并文件）
                cleanupDiskFiles(uploadId);
                uploadService.removeById(task.getFilesystemUploadTaskId());
                count++;
            } catch (Exception e) {
                log.error("清理任务失败: {}", uploadId, e);
            }
        }
        log.info("清理完成，共清理 {} 条过期任务", count);
    }

    /**
     * 清理磁盘临时文件（分片与合并文件）。
     *
     * @param uploadId 上传任务 ID
     */
    private void cleanupDiskFiles(String uploadId) {
        String baseDir = Optional.ofNullable(System.getProperty("filesystem.temp.path"))
                .orElse(uploadProperties.getTempDir());
        Path basePath = Paths.get(baseDir);
        try {
            // 删除合并临时文件
            Files.deleteIfExists(basePath.resolve(uploadId + "_merged"));
            // 删除分片文件
            int totalChunks = Optional.ofNullable(getByUploadId(uploadId))
                    .map(SysFileSystemUpload::getFilesystemUploadTaskTotalChunks)
                    .orElse(0);
            for (int i = 1; i <= totalChunks; i++) {
                Files.deleteIfExists(basePath.resolve(uploadId + "_" + i));
            }
        } catch (IOException e) {
            log.warn("清理磁盘临时文件失败: {}", uploadId, e);
        }
    }

    /**
     * 按 uploadId 查询任务（供清理分片文件时读取分片总数）。
     *
     * @param uploadId 上传任务 ID
     * @return 任务实体
     */
    private SysFileSystemUpload getByUploadId(String uploadId) {
        return getOne(new LambdaQueryWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskUploadId, uploadId));
    }
}
