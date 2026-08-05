package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.monitor.support.entity.MsMonitorMetricConfig;
import com.chua.starter.monitor.support.service.MonitorMetricConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控指标配置 Service 实现
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorMetricConfigServiceImpl implements MonitorMetricConfigService {

    /** JDBC 模板 */
    private final JdbcTemplate jdbcTemplate;

    @Override
    @CacheEvict(allEntries = true, cacheNames = "monitorMetricConfig")
    public ReturnResult<MsMonitorMetricConfig> create(MsMonitorMetricConfig entity) {
        try {
            if (entity.getId() == null) {
                entity.setId(IdUtils.createSnowflakeId());
            }
            jdbcTemplate.update(
                "INSERT INTO ms_monitor_metric_config(id, task_id, metric_name, target_table, parse_rule, " +
                "metric_unit, remark, create_time, update_time) VALUES(?,?,?,?,?,?,?,?,?)",
                entity.getId(), entity.getTaskId(), entity.getMetricName(), entity.getTargetTable(),
                entity.getParseRule(), entity.getMetricUnit(), entity.getRemark(),
                LocalDateTime.now(), LocalDateTime.now());
            return ReturnResult.ok(entity);
        } catch (Exception e) {
            log.error("创建指标配置失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = "monitorMetricConfig")
    public ReturnResult<MsMonitorMetricConfig> update(MsMonitorMetricConfig entity) {
        try {
            jdbcTemplate.update(
                "UPDATE ms_monitor_metric_config SET metric_name=?, target_table=?, parse_rule=?, " +
                "metric_unit=?, remark=?, update_time=? WHERE id=?",
                entity.getMetricName(), entity.getTargetTable(), entity.getParseRule(),
                entity.getMetricUnit(), entity.getRemark(), LocalDateTime.now(), entity.getId());
            return ReturnResult.ok(entity);
        } catch (Exception e) {
            log.error("更新指标配置失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = "monitorMetricConfig")
    public ReturnResult<Void> delete(Long id) {
        try {
            jdbcTemplate.update("DELETE FROM ms_monitor_metric_config WHERE id=?", id);
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
            return ReturnResult.ok(jdbcTemplate.query(
                "SELECT * FROM ms_monitor_metric_config WHERE task_id=?",
                new BeanPropertyRowMapper<>(MsMonitorMetricConfig.class), taskId));
        } catch (Exception e) {
            log.error("按任务查询指标配置失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }
}