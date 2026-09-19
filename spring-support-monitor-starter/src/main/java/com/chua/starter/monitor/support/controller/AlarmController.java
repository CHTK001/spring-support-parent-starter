package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmEvent;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmMaintenanceWindow;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmMaintenanceWindow;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmPolicy;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmRule;
import com.chua.starter.monitor.support.service.alarm.AlarmEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 告警管理接口：策略 / 规则 / 事件
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "监控-告警", description = "告警策略、规则与事件管理")
@RestController
@RequestMapping("/ms/monitor/alarm")
@RequiredArgsConstructor
public class AlarmController {

    /** 告警引擎服务 */
    private final AlarmEngineService alarmEngineService;

    /**
    * 新增策略
    *
    * @param policy 策略
    * @return 结果
    */
    @Operation(summary = "新增告警策略")
    @PostMapping("/policy")
    public ReturnResult<MsMonitorAlarmPolicy> createPolicy(@RequestBody MsMonitorAlarmPolicy policy) {
        return alarmEngineService.createPolicy(policy);
    }

    /**
     * 更新策略
     *
     * @param id     主键
     * @param policy 策略
     * @return 结果
     */
    @Operation(summary = "更新告警策略")
    @PutMapping("/policy/{id}")
    public ReturnResult<MsMonitorAlarmPolicy> updatePolicy(@PathVariable Long id,
                                                           @RequestBody MsMonitorAlarmPolicy policy) {
        return alarmEngineService.updatePolicy(id, policy);
    }

    /**
     * 删除策略（级联删规则）
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除告警策略")
    @DeleteMapping("/policy/{id}")
    public ReturnResult<Void> deletePolicy(@PathVariable Long id) {
        return alarmEngineService.deletePolicy(id);
    }

    /**
     * 策略列表
     *
     * @return 列表
     */
    @Operation(summary = "策略列表")
    @GetMapping("/policy")
    public ReturnResult<List<MsMonitorAlarmPolicy>> listPolicies() {
        return alarmEngineService.listPolicies();
    }

    /**
     * 新增规则
     *
     * @param rule 规则
     * @return 结果
     */
    @Operation(summary = "新增告警规则")
    @PostMapping("/rule")
    public ReturnResult<MsMonitorAlarmRule> createRule(@RequestBody MsMonitorAlarmRule rule) {
        return alarmEngineService.createRule(rule);
    }

    /**
     * 更新规则
     *
     * @param id   主键
     * @param rule 规则
     * @return 结果
     */
    @Operation(summary = "更新告警规则")
    @PutMapping("/rule/{id}")
    public ReturnResult<MsMonitorAlarmRule> updateRule(@PathVariable Long id,
                                                       @RequestBody MsMonitorAlarmRule rule) {
        return alarmEngineService.updateRule(id, rule);
    }

    /**
     * 删除规则
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除告警规则")
    @DeleteMapping("/rule/{id}")
    public ReturnResult<Void> deleteRule(@PathVariable Long id) {
        return alarmEngineService.deleteRule(id);
    }

    /**
     * 规则列表
     *
     * @param policyId 策略过滤，可为空
     * @return 规则列表
     * @param false false
     */
    @Operation(summary = "规则列表")
    @GetMapping("/rule")
    public ReturnResult<List<MsMonitorAlarmRule>> listRules(@RequestParam(required = false) Long policyId) {
        return alarmEngineService.listRules(policyId);
    }

    /**
     * 事件列表
     *
     * @param status 状态过滤，可为空
     * @return 事件列表
     * @param false false
     */
    @Operation(summary = "事件列表")
    @GetMapping("/event")
    public ReturnResult<List<MsMonitorAlarmEvent>> listEvents(@RequestParam(required = false) String status) {
        return alarmEngineService.listEvents(status);
    }

    /**
     * 处理事件
     *
     * @param id         事件 标识
     * @param remark     处理备注
     * @param falseAlarm 是否误报
     * @return 结果
     */
    @Operation(summary = "处理事件")
    @PostMapping("/event/{id}/handle")
    public ReturnResult<Void> handleEvent(@PathVariable Long id,
                                          @RequestParam(required = false) String remark,
                                          @RequestParam(defaultValue = "false") boolean falseAlarm) {
        return alarmEngineService.handleEvent(id, remark, falseAlarm);
    }

    /**
     * 测试告警规则
     *
     * @param ruleId    规则 标识
     * @param monitorId 监控 标识（可选，为空则测试全局规则）
     * @param metricName 指标名
     * @param value     指标值
     * @return 是否触发
     */
    @Operation(summary = "测试告警规则")
    @GetMapping("/rule/{ruleId}/test")
    public ReturnResult<Boolean> testRule(@PathVariable Long ruleId,
                                          @RequestParam(required = false) Long monitorId,
                                          @RequestParam(required = false) String metricName,
                                          @RequestParam(required = false) Double value) {
        return alarmEngineService.testRule(ruleId, monitorId, metricName, value);
    }

    // ==================== 维护窗口 ====================

    /**
     * 新增维护窗口
     *
     * @param window 维护窗口
     * @return 结果
     */
    @Operation(summary = "新增维护窗口")
    @PostMapping("/maintenance-window")
    public ReturnResult<MsMonitorAlarmMaintenanceWindow> createMaintenanceWindow(
            @RequestBody MsMonitorAlarmMaintenanceWindow window) {
        return alarmEngineService.createMaintenanceWindow(window);
    }

    /**
     * 更新维护窗口
     *
     * @param id     主键
     * @param window 维护窗口
     * @return 结果
     */
    @Operation(summary = "更新维护窗口")
    @PutMapping("/maintenance-window/{id}")
    public ReturnResult<MsMonitorAlarmMaintenanceWindow> updateMaintenanceWindow(@PathVariable Long id,
                                                                               @RequestBody MsMonitorAlarmMaintenanceWindow window) {
        return alarmEngineService.updateMaintenanceWindow(id, window);
    }

    /**
     * 删除维护窗口
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除维护窗口")
    @DeleteMapping("/maintenance-window/{id}")
    public ReturnResult<Void> deleteMaintenanceWindow(@PathVariable Long id) {
        return alarmEngineService.deleteMaintenanceWindow(id);
    }

    /**
     * 维护窗口列表
     *
     * @param policyId 策略 标识 过滤（可选）
     * @return 列表
     */
    @Operation(summary = "维护窗口列表")
    @GetMapping("/maintenance-window")
    public ReturnResult<List<MsMonitorAlarmMaintenanceWindow>> listMaintenanceWindows(
            @RequestParam(required = false) Long policyId) {
        return alarmEngineService.listMaintenanceWindows(policyId);
    }

    // ==================== 统计 ====================

    /**
     * 告警概览统计
     *
     * @return 今日总数/未处理/已处理/误报/级别分布
     */
    @Operation(summary = "告警概览统计")
    @GetMapping("/stats/summary")
    public ReturnResult<Map<String, Object>> statsSummary() {
        return alarmEngineService.statsSummary();
    }

    /**
     * 告警趋势
     *
     * @param hours    统计过去 N 小时
     * @param interval 分桶粒度
     * @return 时间序列，元素为 {time, count}
     */
    @Operation(summary = "告警趋势")
    @GetMapping("/stats/trend")
    public ReturnResult<List<Map<String, Object>>> statsTrend(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "1h") String interval) {
        return alarmEngineService.statsTrend(hours, interval);
    }
}
