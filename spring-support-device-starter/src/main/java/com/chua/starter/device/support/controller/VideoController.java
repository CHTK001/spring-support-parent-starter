package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/**
 * 视频控制 Controller
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/api/device/video")
@RequiredArgsConstructor
public class VideoController {

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

    /**
     * 获取视频流地址
     *
     * @param deviceId 设备 ID
     * @param channel  通道号
     * @return 流地址
     */
    @GetMapping("/stream")
    public String getStream(@RequestParam String deviceId, @RequestParam int channel) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            throw new RuntimeException("设备不在线");
        }
        // 实际需返回流媒体地址，此处模拟
        return "ws://localhost:8080/stream/" + deviceId + "/" + channel;
    }

    /**
     * 查询录像列表
     *
     * @param deviceId 设备 ID
     * @param channel  通道号
     * @param start    开始时间 ISO 字符串
     * @param end      结束时间 ISO 字符串
     * @return 录像列表
     */
    @GetMapping("/records")
    public Object queryRecords(@RequestParam String deviceId,
                               @RequestParam int channel,
                               @RequestParam String start,
                               @RequestParam String end) {
        return "录像列表";
    }

    /**
     * 录像回放流
     *
     * @param deviceId 设备 ID
     * @param channel  通道号
     * @param start    开始时间
     * @param end      结束时间
     * @return 回放流
     */
    @GetMapping("/playback")
    public InputStream playback(@RequestParam String deviceId,
                                @RequestParam int channel,
                                @RequestParam String start,
                                @RequestParam String end) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            throw new RuntimeException("设备不在线");
        }
        return null;
    }
}