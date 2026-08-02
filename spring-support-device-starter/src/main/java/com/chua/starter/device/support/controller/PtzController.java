package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.PtzCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            throw new RuntimeException("设备不在线");
        }
        session.ptzControl(cmd, speed);
    }

    /**
     * 设置预置位
     *
     * @param deviceId 设备 ID
     * @param presetId 预置位编号
     * @param name     预置位名称
     */
    @PostMapping("/preset")
    public void setPreset(@RequestParam String deviceId,
                          @RequestParam int presetId,
                          @RequestParam String name) {
        sessionManager.getSession(deviceId).setPreset(presetId, name);
    }

    /**
     * 调用预置位
     *
     * @param deviceId 设备 ID
     * @param presetId 预置位编号
     */
    @PostMapping("/preset/call")
    public void callPreset(@RequestParam String deviceId,
                           @RequestParam int presetId) {
        sessionManager.getSession(deviceId).callPreset(presetId);
    }

    /**
     * 删除预置位
     *
     * @param deviceId 设备 ID
     * @param presetId 预置位编号
     */
    @DeleteMapping("/preset")
    public void deletePreset(@RequestParam String deviceId,
                             @RequestParam int presetId) {
        sessionManager.getSession(deviceId).deletePreset(presetId);
    }

    /**
     * 获取预置位列表
     *
     * @param deviceId 设备 ID
     * @return 预置位列表
     */
    @GetMapping("/preset/list")
    public Object listPresets(@RequestParam String deviceId) {
        return sessionManager.getSession(deviceId).listPresets();
    }
}