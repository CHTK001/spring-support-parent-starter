package com.chua.starter.strategy.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.strategy.entity.SysLimitRecord;
import com.chua.starter.strategy.mapper.SysLimitRecordMapper;
import com.chua.starter.strategy.service.SysLimitRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 限流记录服务实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@Service
public class SysLimitRecordServiceImpl 
        extends ServiceImpl<SysLimitRecordMapper, SysLimitRecord>
        implements SysLimitRecordService {

    @Async
    @Override
    public void saveAsync(SysLimitRecord record) {
        try {
            save(record);
            log.debug("限流记录已保存: {} - {}", record.getSysLimitPath(), record.getClientIp());
        } catch (Exception e) {
            log.error("保存限流记录失败", e);
        }
    }

    @Override
    public int cleanOldRecords(int days) {
        try {
            LocalDateTime beforeDate = LocalDateTime.now().minusDays(days);
            int count = baseMapper.delete(
                    lambdaQuery()
                            .lt(SysLimitRecord::getSysLimitTime, beforeDate)
                            .getWrapper()
            );
            log.info("清理 {} 天前的限流记录，共清理 {} 条", days, count);
            return count;
        } catch (Exception e) {
            log.error("清理限流记录失败", e);
            throw new RuntimeException("清理限流记录失败", e);
        }
    }
}
