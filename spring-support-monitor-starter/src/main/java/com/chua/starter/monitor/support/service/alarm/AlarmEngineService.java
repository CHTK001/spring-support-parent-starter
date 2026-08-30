package com.chua.starter.monitor.support.service.alarm;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmEvent;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmMaintenanceWindow;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmMaintenanceWindow;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmPolicy;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmRule;

import java.util.List;

/**
 * 告警引擎服务
 * <p>
 * 职责：规则缓存管理 + 指标评估（连续触发/静默期去噪）+ 事件落库与通知分发。
 * 评估挂在指标写入链路尾部，拉取式采集与 Agent 推送共用同一条管道。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface AlarmEngineService {

    /**
     * 评估单个指标点是否触发告警
     *
     * @param monitorId   监控目标 ID
     * @param monitorName 目标名称（冗余入事件）
     * @param metricName  指标名
     * @param value       指标值
     */
    void evaluate(Long monitorId, String monitorName, String metricName, double value);

    /**
     * 重新加载规则缓存（策略/规则 CRUD 后调用）
     */
    void reload();

    /**
     * 测试告警规则
     *
     * @param ruleId    规则 ID
     * @param monitorId 监控 ID（可选，为空则测试全局规则）
     * @param metricName 指标名
     * @param value     指标值
     * @return 是否触发告警
     */
    ReturnResult<Boolean> testRule(Long ruleId, Long monitorId, String metricName, Double value);

    // ==================== 维护窗口 ====================

    /**
     * 新增维护窗口
     *
     * @param window 维护窗口
     * @return 结果
     */
    ReturnResult<MsMonitorAlarmMaintenanceWindow> createMaintenanceWindow(
            MsMonitorAlarmMaintenanceWindow window);

    /**
     * 更新维护窗口
     *
     * @param id     主键
     * @param window 维护窗口
     * @return 结果
     */
    ReturnResult<MsMonitorAlarmMaintenanceWindow> updateMaintenanceWindow(Long id,
                                                                        MsMonitorAlarmMaintenanceWindow window);

    /**
     * 删除维护窗口
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<Void> deleteMaintenanceWindow(Long id);

    /**
     * 维护窗口列表
     *
     * @param policyId 策略 ID 过滤（可选）
     * @return 列表
     */
    ReturnResult<List<MsMonitorAlarmMaintenanceWindow>> listMaintenanceWindows(Long policyId);

    // ==================== 策略 CRUD ====================

    /**
     * 新增策略
     *
     * @param policy 策略
     * @return 结果
     */
    ReturnResult<MsMonitorAlarmPolicy> createPolicy(MsMonitorAlarmPolicy policy);

    /**
     * 更新策略
     *
     * @param id     主键
     * @param policy 策略
     * @return 结果
     */
    ReturnResult<MsMonitorAlarmPolicy> updatePolicy(Long id, MsMonitorAlarmPolicy policy);

    /**
     * 删除策略（级联删除规则）
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<Void> deletePolicy(Long id);

    /**
     * 策略列表
     *
     * @return 列表
     */
    ReturnResult<List<MsMonitorAlarmPolicy>> listPolicies();

    // ==================== 规则 CRUD ====================

    /**
     * 新增规则
     *
     * @param rule 规则
     * @return 结果
     */
    ReturnResult<MsMonitorAlarmRule> createRule(MsMonitorAlarmRule rule);

    /**
     * 更新规则
     *
     * @param id   主键
     * @param rule 规则
     * @return 结果
     */
    ReturnResult<MsMonitorAlarmRule> updateRule(Long id, MsMonitorAlarmRule rule);

    /**
     * 删除规则
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<Void> deleteRule(Long id);

    /**
     * 规则列表（可按策略过滤）
     *
     * @param policyId 策略 ID，可为空
     * @return 规则列表
     */
    ReturnResult<List<MsMonitorAlarmRule>> listRules(Long policyId);

    // ==================== 事件管理 ====================

    /**
     * 事件列表（最新在前，上限 200）
     *
     * @param status 状态过滤，可为空
     * @return 事件列表
     */
    ReturnResult<List<MsMonitorAlarmEvent>> listEvents(String status);

    /**
     * 处理事件
     *
     * @param id      事件 ID
     * @param remark  处理备注
     * @param falseAlarm 是否标记为误报
     * @return 结果
     */
    ReturnResult<Void> handleEvent(Long id, String remark, boolean falseAlarm);
}
