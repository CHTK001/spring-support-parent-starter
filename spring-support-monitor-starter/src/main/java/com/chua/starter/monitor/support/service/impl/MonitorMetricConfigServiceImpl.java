package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.monitor.support.entity.MsMonitorMetricConfig;
import com.chua.starter.monitor.support.mapper.MsMonitorMetricConfigMapper;
import com.chua.starter.monitor.support.service.MonitorMetricConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 监控解析规则 Service 实现
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorMetricConfigServiceImpl implements MonitorMetricConfigService {

    /**
     * 缓存名
     */
    public static final String CACHE = "monitorMetricConfig";

    /**
     * Mapper
     */
    private final MsMonitorMetricConfigMapper mapper;

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public MsMonitorMetricConfig create(MsMonitorMetricConfig entity) {
        mapper.insert(entity);
        log.info("[MonitorMetricConfig] 创建解析规则 task={} metric={}",
                entity.getTaskId(), entity.getMetricName());
        return entity;
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public MsMonitorMetricConfig update(MsMonitorMetricConfig entity) {
        mapper.updateById(entity);
        return entity;
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    @Cacheable(cacheNames = CACHE, key = "'monitor.metricConfig:task:' + #taskId")
    public List<MsMonitorMetricConfig> listByTask(Long taskId) {
        LambdaQueryWrapper<MsMonitorMetricConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsMonitorMetricConfig::getTaskId, taskId);
        return mapper.selectList(wrapper);
    }
}