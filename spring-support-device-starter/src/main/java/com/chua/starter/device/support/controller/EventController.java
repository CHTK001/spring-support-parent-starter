package com.chua.starter.device.support.controller;

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
 * 事件查询 Controller
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
     * @param deviceId 设备 ID
     * @param type     事件类型，可选
     * @param start    开始时间，可选
     * @param end      结束时间，可选
     * @return 事件列表
     */
    @GetMapping
    public List<DeviceEventRecord> queryEvents(
            @RequestParam String deviceId,
            @RequestParam(required = false) EventType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return eventService.queryEvents(deviceId, type, start, end);
    }
}