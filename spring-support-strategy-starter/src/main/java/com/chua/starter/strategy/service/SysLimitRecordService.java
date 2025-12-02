package com.chua.starter.strategy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.strategy.entity.SysLimitRecord;

/**
 * 限流记录服务接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
public interface SysLimitRecordService extends IService<SysLimitRecord> {

    /**
     * 异步保存限流记录
     *
     * @param record 限流记录
     */
    void saveAsync(SysLimitRecord record);

    /**
     * 清理指定天数之前的限流记录
     *
     * @param days 天数
     * @return 清理的记录数
     */
    int cleanOldRecords(int days);
}
