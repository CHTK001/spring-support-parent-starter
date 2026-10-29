package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备目录同步控制器（拉取 + 写回两条方向）。
 *
 * <p>拉取：POST /sync，调各厂商 SPI 的 fetchDevices 幂等 upsert 本地台账；</p>
 * <p>写回：POST /{id}/push 把本地台账推到厂商侧，DELETE /{id}/remote
 * 按厂商侧唯一标识删除远端设备（厂商不支持写回时返回 false）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/device/sync")
@RequiredArgsConstructor
public class DeviceSyncController {

    /**
     * 设备目录同步服务
     */
    private final DeviceSyncService syncService;

    /**
     * 设备服务（取台账详情）
     */
    private final DeviceService deviceService;

    /**
     * 同步厂商设备目录（拉取方向）
     *
     * @param vendor 厂商标识，可空；为空时同步全部已注册厂商
     * @return created / updated / skipped 统计
     */
    @PostMapping("")
    public ReturnResult<Map<String, Integer>> syncDevices(@RequestParam(required = false) String vendor) {
        return ReturnResult.ok(syncService.syncDevices(vendor));
    }

    /**
     * 把单台本地设备写回厂商侧（推送方向）
     *
     * @param id 本地设备 ID
     * @return 是否写回成功
     */
    @PostMapping("/{id}/push")
    public ReturnResult<Boolean> pushDevice(@PathVariable String id) {
        DeviceInfo device = deviceService.getDevice(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: " + id);
        }
        return ReturnResult.ok(syncService.pushDevice(device));
    }

    /**
     * 删除厂商侧设备（推送方向）
     *
     * @param id 本地设备 ID
     * @return 是否删除成功；厂商不支持时为 false
     */
    @DeleteMapping("/{id}/remote")
    public ReturnResult<Boolean> removeRemote(@PathVariable String id) {
        DeviceInfo device = deviceService.getDevice(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: " + id);
        }
        return ReturnResult.ok(syncService.removeRemote(device));
    }

    /**
     * 按本地设备信息直接推送（不要求设备已落台账，供调用方灵活使用）
     *
     * @param device 本地台账设备
     * @return 是否写回成功
     */
    @PostMapping("/push")
    public ReturnResult<Boolean> pushDeviceDirect(@RequestBody DeviceInfo device) {
        if (device == null || device.getDeviceInfoId() == null) {
            throw new IllegalArgumentException("设备信息不能为空");
        }
        return ReturnResult.ok(syncService.pushDevice(device));
    }
}