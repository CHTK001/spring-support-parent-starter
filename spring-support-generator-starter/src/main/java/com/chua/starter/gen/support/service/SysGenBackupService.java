package com.chua.starter.gen.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGenBackup;

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
}
