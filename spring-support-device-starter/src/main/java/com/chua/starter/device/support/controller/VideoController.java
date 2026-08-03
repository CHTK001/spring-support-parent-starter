package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/**
 * 视频控制 Controller
 * <p>对外提供设备视频流地址获取与回放代理，</p>
 * <p>前端通过 {@code /api/device/video/stream?deviceId=&channel=&protocol=hls} 获取拉流 URL，</p>
 * <p>前端 hls.js/flv.js 播放器直接播放该 URL。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
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
     * @param protocol 协议类型，默认 hls
     * @return 拉流 URL（前端用 hls.js/flv.js 播放）
     */
    @GetMapping("/stream")
    public String getStream(@RequestParam String deviceId,
                            @RequestParam int channel,
                            @RequestParam(required = false, defaultValue = "hls") String protocol) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            throw new RuntimeException("设备不在线: " + deviceId);
        }
        String url = session.getLiveUrl(channel, protocol);
        if (url == null || url.isEmpty()) {
            throw new RuntimeException("设备 " + deviceId + " 未返回拉流地址，请检查平台账号配置");
        }
        log.debug("设备拉流地址: deviceId={}, channel={}, protocol={}, url={}",
                deviceId, channel, protocol, mask(url));
        return url;
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

    /**
     * 拉流 URL 脱敏（隐藏 token / signature 段）
     */
    private static String mask(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q) + "?***";
    }
}
