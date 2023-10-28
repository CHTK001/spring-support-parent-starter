package com.chua.starter.device.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.device.support.entity.*;
import com.chua.starter.device.support.request.EventType;
import com.chua.starter.device.support.service.DeviceDataAccessEventService;
import com.chua.starter.device.support.service.DeviceDataEventService;
import com.chua.starter.device.support.service.DeviceInfoService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import lombok.AllArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
     * 分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("page")
    public ReturnPageResult<? extends DeviceDataEvent> page(
            String deviceId,
            String deviceIsmi,
            EventType eventType,
            @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        if(null == eventType) {
            return ReturnPageResult.ok(Collections.emptyList());
        }

        DeviceInfo deviceInfo = deviceInfoService.getDeviceInfo(deviceId, deviceIsmi);
        return deviceDataEventService.page(eventType, pageNum, pageSize, deviceInfo);
    }


}
