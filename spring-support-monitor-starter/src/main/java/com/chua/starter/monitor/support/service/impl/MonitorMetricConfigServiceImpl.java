package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.monitor.support.entity.MsMonitorMetricConfig;
import com.chua.starter.monitor.support.mapper.MsMonitorMetricConfigMapper;
import com.chua.starter.monitor.support.service.MonitorMetricConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorMetricConfigServiceImpl implements MonitorMetricConfigService {

    private final MsMonitorMetricConfigMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorMetricConfig")
    public ReturnResult<MsMonitorMetricConfig> create(MsMonitorMetricConfig entity) {
        try {
            if (entity.getId() == null) {
                entity.setId(IdUtils.createSnowflakeId());
            }
            mapper.insert(entity);
            return ReturnResult.ok(entity);
        } catch (Exception e) {
            log.error("创建指标配置失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorMetricConfig")
    public ReturnResult<MsMonitorMetricConfig> update(MsMonitorMetricConfig entity) {
        try {
            mapper.updateById(entity);
            return ReturnResult.ok(entity);
        } catch (Exception e) {
            log.error("更新指标配置失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorMetricConfig")
    public ReturnResult<Void> delete(Long id) {
        try {
            mapper.deleteById(id);
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("删除指标配置失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Cacheable(cacheNames = "monitorMetricConfig", key = "'monitor.metricConfig:task:' + #taskId")
    public ReturnResult<List<MsMonitorMetricConfig>> listByTask(Long taskId) {
        try {
            return ReturnResult.ok(mapper.selectList(
                new LambdaQueryWrapper<MsMonitorMetricConfig>()
                    .eq(MsMonitorMetricConfig::getTaskId, taskId)));
        } catch (Exception e) {
            log.error("按任务查询指标配置失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }
}