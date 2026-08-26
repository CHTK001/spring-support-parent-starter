package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmEvent;
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
     */
    @Operation(summary = "事件列表")
    @GetMapping("/event")
    public ReturnResult<List<MsMonitorAlarmEvent>> listEvents(@RequestParam(required = false) String status) {
        return alarmEngineService.listEvents(status);
    }

    /**
     * 处理事件
     *
     * @param id         事件 ID
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
}
