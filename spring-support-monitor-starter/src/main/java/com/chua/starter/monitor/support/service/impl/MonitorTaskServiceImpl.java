package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import com.chua.starter.monitor.support.mapper.MsMonitorTaskMapper;
import com.chua.starter.monitor.support.service.MonitorTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控任务 Service 实现
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorTaskServiceImpl implements MonitorTaskService {

    /** 任务 Mapper */
    private final MsMonitorTaskMapper taskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorTask")
    /** 创建 */
    public ReturnResult<MsMonitorTask> create(MsMonitorTask task) {
        try {
            if (task.getMonitorTaskEnabled() == null) {
                task.setMonitorTaskEnabled(1);
            }
            if (task.getMonitorTaskTimeout() == null) {
                task.setMonitorTaskTimeout(5000);
            }
            taskMapper.insert(task);
            return ReturnResult.ok(task);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorTask")
    /** 更新 */
    public ReturnResult<MsMonitorTask> update(MsMonitorTask task) {
        try {
            taskMapper.updateById(task);
            return ReturnResult.ok(task);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorTask")
    /** 删除 */
    public ReturnResult<Void> delete(Long id) {
        try {
            taskMapper.deleteById(id);
            return ReturnResult.ok();
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Cacheable(cacheNames = "monitorTask", key = "'monitor.task:id:' + #id")
    /** 查找ById */
    public ReturnResult<MsMonitorTask> findById(Long id) {
        try {
            return ReturnResult.ok(taskMapper.selectById(id));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Cacheable(cacheNames = "monitorTask", key = "'monitor.task:enabled'")
    /** ListEnabled */
    public ReturnResult<List<MsMonitorTask>> listEnabled() {
        try {
            LambdaQueryWrapper<MsMonitorTask> w = new LambdaQueryWrapper<>();
            w.eq(MsMonitorTask::getMonitorTaskEnabled, 1);
            return ReturnResult.ok(taskMapper.selectList(w));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Cacheable(cacheNames = "monitorTask", key = "'monitor.task:monitor:' + #monitorId")
    /** ListByMonitor */
    public ReturnResult<List<MsMonitorTask>> listByMonitor(Long monitorId) {
        try {
            LambdaQueryWrapper<MsMonitorTask> w = new LambdaQueryWrapper<>();
            w.eq(MsMonitorTask::getMonitorId, monitorId);
            return ReturnResult.ok(taskMapper.selectList(w));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorTask")
    /** 设置Enabled */
    public ReturnResult<Void> setEnabled(Long id, Integer enabled) {
        try {
            MsMonitorTask t = new MsMonitorTask();
            t.setId(id);
            t.setMonitorTaskEnabled(enabled);
            taskMapper.updateById(t);
            return ReturnResult.ok();
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = "monitorTask")
    /** 标记Last运行Time */
    public ReturnResult<Void> markLastRunTime(Long id) {
        try {
            MsMonitorTask t = new MsMonitorTask();
            t.setId(id);
            t.setLastRunTime(LocalDateTime.now());
            taskMapper.updateById(t);
            return ReturnResult.ok();
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }
}