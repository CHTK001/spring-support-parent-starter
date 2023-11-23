package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceDataEvent;
import com.chua.starter.device.support.entity.DeviceDict;
import com.chua.starter.device.support.request.EventRequest;
import com.chua.starter.device.support.service.DeviceDataAccessEventService;
import com.chua.starter.device.support.service.DeviceDataEventService;
import com.chua.starter.device.support.service.DeviceInfoService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * 设备传感器/管道控制器
 *
 * @author CH
 */
@RestController
@AllArgsConstructor
@RequestMapping("v1/device/data")
public class DeviceDataController {

    private final DeviceInfoService deviceInfoService;

    private final DeviceDataAccessEventService deviceDataAccessEventService;
    private final DeviceDataEventService deviceDataEventService;

    /**
     * 门禁分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PostMapping("page")
    public ReturnPageResult<? extends DeviceDataEvent> page(@RequestBody EventRequest request) {

        if(null == request.getEventType()) {
            return ReturnPageResult.ok(Collections.emptyList());
        }

        return deviceDataEventService.page(request, null);
    }
    /**
     * 气象站分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PostMapping("weather/page")
    public ReturnPageResult<? extends DeviceDataEvent> weather(@RequestBody EventRequest request) {

        if(null == request.getEventType()) {
            return ReturnPageResult.ok(Collections.emptyList());
        }

        return deviceDataEventService.page(request, null);
    }


}
