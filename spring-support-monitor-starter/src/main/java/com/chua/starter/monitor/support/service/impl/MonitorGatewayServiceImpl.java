package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.monitor.support.entity.MsMonitorGateway;
import com.chua.starter.monitor.support.mapper.MsMonitorGatewayMapper;
import com.chua.starter.monitor.support.service.MonitorGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 监控网关 Service 实现
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorGatewayServiceImpl implements MonitorGatewayService {

    /**
     * 缓存名
     */
    public static final String CACHE = "monitorGateway";

    /**
     * Mapper
     */
    private final MsMonitorGatewayMapper mapper;

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public MsMonitorGateway create(MsMonitorGateway entity) {
        if (entity.getGatewayEnabled() == null) {
            entity.setGatewayEnabled(1);
        }
        if (entity.getGatewayType() == null) {
            entity.setGatewayType("GUACAMOLE");
        }
        mapper.insert(entity);
        log.info("[MonitorGateway] 创建网关 id={} name={}", entity.getId(), entity.getGatewayName());
        return entity;
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public MsMonitorGateway update(MsMonitorGateway entity) {
        mapper.updateById(entity);
        return entity;
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    @Cacheable(cacheNames = CACHE, key = "'monitor.gateway:monitor:' + #monitorId")
    public List<MsMonitorGateway> listByMonitor(Long monitorId) {
        LambdaQueryWrapper<MsMonitorGateway> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsMonitorGateway::getMonitorId, monitorId);
        return mapper.selectList(wrapper);
    }
}