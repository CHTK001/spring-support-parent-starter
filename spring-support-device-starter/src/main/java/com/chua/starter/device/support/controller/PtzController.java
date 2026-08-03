package com.chua.starter.device.support.controller;

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
 * 云台控制 Controller
 *
 * @author CH
 * @since 4.0.0.42
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
     * @param deviceId 设备 ID
     * @param cmd      控制指令
     * @param speed    速度（0-1）
     */
    @PostMapping("/control")
    public void control(@RequestParam String deviceId,
                        @RequestParam PtzCommand cmd,
                        @RequestParam(defaultValue = "0.5") double speed) {
        DeviceSession session = requireOnline(deviceId);
        session.ptzControl(cmd, speed);
    }

    /**
     * 设置预置位
     */
    @PostMapping("/preset")
    public void setPreset(@RequestParam String deviceId,
                          @RequestParam int presetId,
                          @RequestParam String name) {
        requireOnline(deviceId).setPreset(presetId, name);
    }

    /**
     * 调用预置位
     */
    @PostMapping("/preset/call")
    public void callPreset(@RequestParam String deviceId,
                           @RequestParam int presetId) {
        requireOnline(deviceId).callPreset(presetId);
    }

    /**
     * 删除预置位
     */
    @DeleteMapping("/preset")
    public void deletePreset(@RequestParam String deviceId,
                             @RequestParam int presetId) {
        requireOnline(deviceId).deletePreset(presetId);
    }

    /**
     * 获取预置位列表
     */
    @GetMapping("/preset/list")
    public List<PresetInfo> listPresets(@RequestParam String deviceId) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            return Collections.emptyList();
        }
        return session.listPresets();
    }

    private DeviceSession requireOnline(String deviceId) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            throw new RuntimeException("设备不在线: " + deviceId);
        }
        return session;
    }
}
