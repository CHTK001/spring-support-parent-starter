package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceEventRecord;
import com.chua.starter.device.support.service.DeviceEventService;
import com.chua.starter.device.spi.model.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件查询 控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/device/events")
@RequiredArgsConstructor
public class EventController {

    /**
     * 事件服务
     */
    private final DeviceEventService eventService;

    /**
     * 查询事件
     *
     * @param deviceId 设备 标识
     * @param type     事件类型，可空
     * @param start    开始时间，可空
     * @param end      结束时间，可空
     * @return 事件列表
     */
    @GetMapping
    public ReturnResult<List<DeviceEventRecord>> queryEvents(
            @RequestParam String deviceId,
            @RequestParam(required = false) EventType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ReturnResult.ok(eventService.queryEvents(deviceId, type, start, end));
    }
}
