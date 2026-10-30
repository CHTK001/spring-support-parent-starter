package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.model.DeviceTopologyResult;
import com.chua.starter.device.support.service.DeviceTopologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 机房拓扑控制器
 * <p>从设备台账（{@code device_info}）与通道台账（{@code device_channel}）拼接：
 * 楼层/区域（area）→ 网段（subnet）→ 设备（device）→ 通道（channel）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/device/topology")
@RequiredArgsConstructor
public class DeviceTopologyController {

    /** 拓扑服务 */
    private final DeviceTopologyService topologyService;

    @GetMapping
    public ReturnResult<DeviceTopologyResult> topology() {
        return ReturnResult.ok(topologyService.topology());
    }
}
