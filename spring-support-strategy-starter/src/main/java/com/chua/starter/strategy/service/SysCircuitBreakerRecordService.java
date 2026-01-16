package com.chua.starter.strategy.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.strategy.entity.SysCircuitBreakerRecord;

/**
 * 熔断记录服务接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
public interface SysCircuitBreakerRecordService extends IService<SysCircuitBreakerRecord> {

    /**
     * 分页查询熔断记录
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 分页结果
     */
    IPage<SysCircuitBreakerRecord> pageForStrategy(IPage<SysCircuitBreakerRecord> page, 
                                                    SysCircuitBreakerRecord entity);

    /**
     * 保存熔断记录
     *
     * @param record 熔断记录
     */
    void saveCircuitBreakerRecord(SysCircuitBreakerRecord record);

    /**
     * 清理指定天数之前的熔断记录
     *
     * @param days 天数
     * @return 清理的记录数
     */
    int cleanRecordsBefore(int days);
}
