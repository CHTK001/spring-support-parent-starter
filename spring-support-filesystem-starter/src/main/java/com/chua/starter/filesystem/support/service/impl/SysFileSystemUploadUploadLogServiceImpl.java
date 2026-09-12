package com.chua.starter.filesystem.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadUploadLog;
import com.chua.starter.filesystem.support.mapper.SysFileSystemUploadUploadLogMapper;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadUploadLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
   * 文件系统上传 对象存储 记录服务实现。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class SysFileSystemUploadUploadLogServiceImpl
        extends ServiceImpl<SysFileSystemUploadUploadLogMapper, SysFileSystemUploadUploadLog>
        implements SysFileSystemUploadUploadLogService {

    /**
      * 系统默认操作人 标识
     */
    private static final int SYSTEM_USER_ID = 0;

    /**
     * 系统默认操作人名称
     */
    private static final String SYSTEM_USER_NAME = "system";

    @Override
    @Transactional
    /**
     * Record
     *
     * @param uploadId uploadid
     * @param success 成功
     * @param message 消息
     * @param retryCount 重试数量
     * @return record的结果
     */
    public boolean record(String uploadId, boolean success, String message, int retryCount) {
        SysFileSystemUploadUploadLog log = new SysFileSystemUploadUploadLog();
        LocalDateTime now = LocalDateTime.now();
        log.setFilesystemUploadUploadLogUploadId(uploadId);
        log.setFilesystemUploadUploadLogStage(SysFileSystemUploadUploadLog.Stage.UPLOAD);
        log.setFilesystemUploadUploadLogStatus(success
                ? SysFileSystemUploadUploadLog.Result.SUCCESS
                : SysFileSystemUploadUploadLog.Result.FAILED);
        log.setFilesystemUploadUploadLogMessage(message);
        log.setFilesystemUploadUploadLogRetryCount(retryCount);
        log.setFilesystemUploadUploadLogStartTime(now);
        log.setFilesystemUploadUploadLogEndTime(now);
        log.setCreateTime(now);
        log.setUpdateTime(now);
        log.setCreateBy(SYSTEM_USER_ID);
        log.setCreateName(SYSTEM_USER_NAME);
        log.setUpdateBy(SYSTEM_USER_ID);
        log.setUpdateName(SYSTEM_USER_NAME);
        return save(log);
    }

    @Override
    /** 列表byuploadid */
    public List<SysFileSystemUploadUploadLog> listByUploadId(String uploadId) {
        return list(new LambdaQueryWrapper<SysFileSystemUploadUploadLog>()
                .eq(SysFileSystemUploadUploadLog::getFilesystemUploadUploadLogUploadId, uploadId)
                .orderByAsc(SysFileSystemUploadUploadLog::getFilesystemUploadUploadLogStartTime));
    }

    @Override
    @Transactional
    /**
     * 删除byuploadid
     *
     * @param uploadId uploadid
     * @return 删除byuploadid的结果
     */
    public boolean deleteByUploadId(String uploadId) {
        return remove(new LambdaQueryWrapper<SysFileSystemUploadUploadLog>()
                .eq(SysFileSystemUploadUploadLog::getFilesystemUploadUploadLogUploadId, uploadId));
    }
}
