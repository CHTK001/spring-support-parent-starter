package com.chua.starter.strategy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.strategy.entity.SysCircuitBreakerRecord;
import com.chua.starter.strategy.mapper.SysCircuitBreakerRecordMapper;
import com.chua.starter.strategy.service.SysCircuitBreakerRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 熔断记录服务实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@Service
public class SysCircuitBreakerRecordServiceImpl 
        extends ServiceImpl<SysCircuitBreakerRecordMapper, SysCircuitBreakerRecord>
        implements SysCircuitBreakerRecordService {

    @Override
    public IPage<SysCircuitBreakerRecord> pageForStrategy(IPage<SysCircuitBreakerRecord> page, 
                                                           SysCircuitBreakerRecord entity) {
        LambdaQueryWrapper<SysCircuitBreakerRecord> wrapper = new LambdaQueryWrapper<>();

        // 按熔断器ID查询
        if (entity.getSysCircuitBreakerId() != null) {
            wrapper.eq(SysCircuitBreakerRecord::getSysCircuitBreakerId, entity.getSysCircuitBreakerId());
        }

        // 按熔断器名称模糊查询
        if (StringUtils.isNotBlank(entity.getSysCircuitBreakerName())) {
            wrapper.like(SysCircuitBreakerRecord::getSysCircuitBreakerName, entity.getSysCircuitBreakerName());
        }

        // 按接口路径模糊查询
        if (StringUtils.isNotBlank(entity.getSysCircuitBreakerPath())) {
            wrapper.like(SysCircuitBreakerRecord::getSysCircuitBreakerPath, entity.getSysCircuitBreakerPath());
        }

        // 按熔断器状态查询
        if (StringUtils.isNotBlank(entity.getCircuitBreakerState())) {
            wrapper.eq(SysCircuitBreakerRecord::getCircuitBreakerState, entity.getCircuitBreakerState());
        }

        // 按触发时间倒序排列
        wrapper.orderByDesc(SysCircuitBreakerRecord::getTriggerTime);

        return page(page, wrapper);
    }

    @Override
    @Async
    public void saveCircuitBreakerRecord(SysCircuitBreakerRecord record) {
        try {
            if (record.getTriggerTime() == null) {
                record.setTriggerTime(LocalDateTime.now());
            }
            save(record);
            log.debug("保存熔断记录成功: path={}, state={}", 
                    record.getSysCircuitBreakerPath(), record.getCircuitBreakerState());
        } catch (Exception e) {
            log.error("保存熔断记录失败: path={}", record.getSysCircuitBreakerPath(), e);
        }
    }

    @Override
    public int cleanRecordsBefore(int days) {
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<SysCircuitBreakerRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(SysCircuitBreakerRecord::getTriggerTime, beforeTime);
        
        int count = Math.toIntExact(count(wrapper));
        if (count > 0) {
            remove(wrapper);
            log.info("清理 {} 天前的熔断记录，共 {} 条", days, count);
        }
        return count;
    }
}
