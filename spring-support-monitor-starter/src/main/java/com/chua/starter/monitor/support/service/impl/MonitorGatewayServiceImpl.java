package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.monitor.support.entity.MsMonitorGateway;
import com.chua.starter.monitor.support.mapper.MsMonitorGatewayMapper;
import com.chua.starter.monitor.support.service.MonitorGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 监控网关 服务 实现
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorGatewayServiceImpl implements MonitorGatewayService {

    /** 网关 映射器 */
    private final MsMonitorGatewayMapper gatewayMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorGateway")
    /**
     * 创建。
     * @param entity 实体
     * @return 创建的结果
     */
    public ReturnResult<MsMonitorGateway> create(MsMonitorGateway entity) {
        try {
            if (entity.getId() == null) {
                entity.setId(IdUtils.createSnowflakeId());
            }
            if (entity.getGatewayEnabled() == null) {
                entity.setGatewayEnabled(1);
            }
            if (entity.getGatewayType() == null) {
                entity.setGatewayType("GUACAMOLE");
            }
            gatewayMapper.insert(entity);
            return ReturnResult.ok(entity);
        } catch (Exception e) {
            log.error("创建网关失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorGateway")
    /**
     * 更新。
     * @param entity 实体
     * @return 更新的结果
     */
    public ReturnResult<MsMonitorGateway> update(MsMonitorGateway entity) {
        try {
            gatewayMapper.updateById(entity);
            return ReturnResult.ok(entity);
        } catch (Exception e) {
            log.error("更新网关失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorGateway")
    /**
     * 删除。
     * @param id 标识
     * @return 删除的结果
     */
    public ReturnResult<Void> delete(Long id) {
        try {
            gatewayMapper.deleteById(id);
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("删除网关失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Cacheable(cacheNames = "monitorGateway", key = "'monitor.gateway:monitor:' + #monitorId")
    /**
     * 列表by监控。
     * @param monitorId 监控标识
     * @return 列表by监控的结果
     */
    public ReturnResult<List<MsMonitorGateway>> listByMonitor(Long monitorId) {
        try {
            LambdaQueryWrapper<MsMonitorGateway> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MsMonitorGateway::getMonitorId, monitorId);
            return ReturnResult.ok(gatewayMapper.selectList(wrapper));
        } catch (Exception e) {
            log.error("按主机查询网关失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }
}
