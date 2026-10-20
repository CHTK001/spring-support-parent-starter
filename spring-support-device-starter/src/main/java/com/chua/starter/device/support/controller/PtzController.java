package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 云台控制 控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/device/ptz")
@RequiredArgsConstructor
public class PtzController {

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

    /**
     * 云台控制
     *
     * @param deviceId 设备 标识
     * @param cmd      控制指令
     * @param speed    速度（0-1）
     * @return 操作结果
     */
    @PostMapping("/control")
    public ReturnResult<Void> control(@RequestParam String deviceId,
                                      @RequestParam PtzCommand cmd,
                                      @RequestParam(defaultValue = "0.5") double speed) {
        requireOnline(deviceId).ptzControl(cmd, speed);
        return ReturnResult.ok();
    }

    /**
     * 设置预置点
     *
     * @param deviceId 设备 标识
     * @param presetId 预置点编号
     * @param name     预置点名称
     * @return 操作结果
     */
    @PostMapping("/preset")
    public ReturnResult<Void> setPreset(@RequestParam String deviceId,
                                        @RequestParam int presetId,
                                        @RequestParam String name) {
        requireOnline(deviceId).setPreset(presetId, name);
        return ReturnResult.ok();
    }

    /**
     * 调用预置点
     *
     * @param deviceId 设备 标识
     * @param presetId 预置点编号
     * @return 操作结果
     */
    @PostMapping("/preset/call")
    public ReturnResult<Void> callPreset(@RequestParam String deviceId,
                                         @RequestParam int presetId) {
        requireOnline(deviceId).callPreset(presetId);
        return ReturnResult.ok();
    }

    /**
     * 删除预置点
     *
     * @param deviceId 设备 标识
     * @param presetId 预置点编号
     * @return 操作结果
     */
    @DeleteMapping("/preset")
    public ReturnResult<Void> deletePreset(@RequestParam String deviceId,
                                           @RequestParam int presetId) {
        requireOnline(deviceId).deletePreset(presetId);
        return ReturnResult.ok();
    }

    /**
     * 获取预置点列表
     *
     * @param deviceId 设备 标识
     * @return 预置点列表
     */
    @GetMapping("/preset/list")
    public ReturnResult<List<PresetInfo>> listPresets(@RequestParam String deviceId) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            return ReturnResult.ok(Collections.emptyList());
        }
        return ReturnResult.ok(session.listPresets());
    }

    /**
     * 校验设备在线并获取会话
     *
     * @param deviceId 设备 标识
     * @return 设备会话
     */
    private DeviceSession requireOnline(String deviceId) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            throw new RuntimeException("设备不在线: " + deviceId);
        }
        return session;
    }
}
