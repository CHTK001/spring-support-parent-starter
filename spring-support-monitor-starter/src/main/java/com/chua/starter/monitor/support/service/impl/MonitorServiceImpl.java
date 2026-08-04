package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.mapper.MsMonitorMapper;
import com.chua.starter.monitor.support.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 监控资产 Service 实现
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    /**
     * 资产缓存名
     */
    public static final String CACHE = "monitor";

    /**
     * Mapper
     */
    private final MsMonitorMapper monitorMapper;

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public MsMonitor create(MsMonitor monitor) {
        if (monitor.getMonitorEnabled() == null) {
            monitor.setMonitorEnabled(1);
        }
        monitorMapper.insert(monitor);
        log.info("[Monitor] 创建资产 id={} name={}", monitor.getId(), monitor.getMonitorName());
        return monitor;
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public MsMonitor update(MsMonitor monitor) {
        monitorMapper.updateById(monitor);
        log.info("[Monitor] 更新资产 id={}", monitor.getId());
        return monitor;
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public void delete(Long id) {
        monitorMapper.deleteById(id);
        log.info("[Monitor] 删除资产 id={}", id);
    }

    @Override
    @Cacheable(cacheNames = CACHE, key = "'monitor:id:' + #id")
    public MsMonitor findById(Long id) {
        return monitorMapper.selectById(id);
    }

    @Override
    @Cacheable(cacheNames = CACHE, key = "'monitor:list:all'")
    public List<MsMonitor> listAll() {
        return monitorMapper.selectList(null);
    }

    @Override
    public List<MsMonitor> listByServiceType(String serviceType) {
        LambdaQueryWrapper<MsMonitor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsMonitor::getServiceType, serviceType);
        wrapper.eq(MsMonitor::getMonitorEnabled, 1);
        return monitorMapper.selectList(wrapper);
    }

    @Override
    public Page<MsMonitor> page(long pageNum, long pageSize, String keyword) {
        Page<MsMonitor> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MsMonitor> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(MsMonitor::getMonitorName, keyword);
        }
        wrapper.orderByDesc(MsMonitor::getId);
        return monitorMapper.selectPage(page, wrapper);
    }
}