package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceAlarm;
import com.chua.starter.device.support.model.AlarmQuery;
import com.chua.starter.device.support.service.DeviceAlarmService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备告警查询与处理接口（供告警页签使用）。
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequestMapping("/api/device/alarms")
@RequiredArgsConstructor
public class DeviceAlarmController {

    /** 告警服务 */
    private final DeviceAlarmService alarmService;

    /**
     * 概览指标。
     *
     * @return 总数 / 未处理 / 严重 / 今日新增
     */
    @GetMapping("/stats")
    public ReturnResult<Map<String, Object>> stats() {
        return ReturnResult.ok(alarmService.stats());
    }

    /**
     * 分页查询告警。
     *
     * @param query 筛选条件（pageNo / pageSize / level / status / deviceId / type / keyword / start / end）
     * @return 分页结果
     */
    @GetMapping
    public ReturnResult<PageResult<DeviceAlarm>> page(AlarmQuery query) {
        return ReturnResult.ok(alarmService.page(query));
    }

    /**
     * 更新处理状态。
     *
     * @param id   告警 ID
     * @param body status 目标状态、handler 处理人
     * @return 更新后的告警
     */
    @PutMapping("/{id}/status")
    public ReturnResult<DeviceAlarm> updateStatus(@PathVariable String id,
                                                  @RequestBody Map<String, String> body) {
        return ReturnResult.ok(alarmService.updateStatus(id, body.get("status"), body.get("handler")));
    }
}
