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
/** @作者 CH */

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorMetricConfigServiceImpl implements MonitorMetricConfigService {

    /** 映射器 */
    private final MsMonitorMetricConfigMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorMetricConfig")
    /**
     * 创建
     *
     * @param entity 实体
     * @return 创建的结果
     */
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
    /**
     * 更新
     *
     * @param entity 实体
     * @return 更新的结果
     */
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
    /**
     * 删除
     *
     * @param id 标识
     * @return 删除的结果
     */
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
    /**
     * 列表by任务
     *
     * @param taskId 任务标识
     * @return 列表by任务的结果
     */
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
