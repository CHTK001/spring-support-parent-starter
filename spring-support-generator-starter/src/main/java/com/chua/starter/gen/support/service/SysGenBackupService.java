package com.chua.starter.gen.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.backup.BackupDriver;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.gen.support.entity.SysGenBackup;

import java.util.List;

/**
 * @author CH
 */
public interface SysGenBackupService extends IService<SysGenBackup> {


    /**
     * 停止
     *
     * @param genId gen id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    ReturnResult<Boolean> stop(Integer genId);

    /**
     * 开始
     *
     * @param genId gen id
     * @return {@link ReturnResult}<{@link SysGenBackup}>
     */
    ReturnResult<SysGenBackup> start(Integer genId);

    /**
     * 使现代化
     *
     * @param sysGenBackup sys gen备份
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    ReturnResult<Boolean> update(SysGenBackup sysGenBackup);

    /**
     * 驾驶员
     *
     * @param genId gen id
     * @return {@link List}<{@link String}>
     */
    ReturnResult<List<BackupDriver>> driver(Integer genId);
}
