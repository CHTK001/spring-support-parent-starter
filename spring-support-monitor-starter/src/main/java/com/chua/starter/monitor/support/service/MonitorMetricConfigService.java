package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitorMetricConfig;

import java.util.List;

/**
 * 监控解析规则 服务
 *
 * @since 2026/8/4
 * @author CH
 */
public interface MonitorMetricConfigService {

    /**
     * 创建
     *
     * @param entity 实体
     * @return 创建结果
     */
    ReturnResult<MsMonitorMetricConfig> create(MsMonitorMetricConfig entity);

    /**
     * 更新
     *
     * @param entity 实体
     * @return 更新结果
     */
    ReturnResult<MsMonitorMetricConfig> update(MsMonitorMetricConfig entity);

    /**
     * 删除
     *
     * @param id 主键
     * @return 删除结果
     */
    ReturnResult<Void> delete(Long id);

    /**
     * 按任务列出
     *
     * @param taskId 任务 标识
     * @return 列表
     */
    ReturnResult<List<MsMonitorMetricConfig>> listByTask(Long taskId);
}
