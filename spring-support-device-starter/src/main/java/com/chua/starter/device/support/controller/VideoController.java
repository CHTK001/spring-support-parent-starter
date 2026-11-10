package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.RecordFile;
import com.chua.starter.device.spi.model.LiveHandle;
import com.chua.starter.device.spi.model.TimeRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 视频控制 控制器
 * <p>对外提供设备视频流地址获取、录像列表查询、回放 URL 获取。</p>
 *
 * <p>前端通过 {@code /api/device/video/stream} 获取拉流 URL。</p>
 * <p>通过 {@code /api/device/video/playback} 获取录像回放 URL。</p>
 * <p>前端 hls.js/flv.js 播放器直接播放这些 URL。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/device/video")
@RequiredArgsConstructor
public class VideoController {

    /**
     * 时间格式
     */
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;
    private final DeviceService deviceService;

    /**
     * 获取实时视频流地址
     *
     * @param deviceId   设备 标识
     * @param channel    通道号
     * @param protocol   协议类型，默认 hls
     * @param streamType 码流：main 主码流 / sub 子码流，默认 main
     * @return 拉流 URL
     */
    @GetMapping("/stream")
    public ReturnResult<String> getStream(@RequestParam String deviceId,
                                          @RequestParam int channel,
                                          @RequestParam(required = false, defaultValue = "hls") String protocol,
                                          @RequestParam(required = false, defaultValue = "main") String streamType) {
        DeviceSession session = requireOnline(deviceId);
        String url = session.getLiveUrl(channel, protocol, streamType);
        if (url == null || url.isEmpty()) {
            throw new RuntimeException("设备 " + deviceId + " 未返回拉流地址，请检查平台账号配置");
        }
        log.debug("设备拉流地址: deviceId={}, channel={}, protocol={}, streamType={}, url={}",
                deviceId, channel, protocol, streamType, mask(url));
        return ReturnResult.ok(url);
    }

    /**
     * 开启直播：显式建立一次预览会话，返回可播放地址与会话标识。
     *
     * <p>与 {@code /stream} 的区别：本端点强调「开启/停止」生命周期，
     * SDK 直连会真实占用厂商侧句柄，用完必须调 {@code /live/stop}。</p>
     *
     * @param deviceId   设备标识
     * @param channel    通道号
     * @param protocol   协议类型，默认 hls
     * @param streamType 码流，默认 main
     * @return 直播句柄
     */
    @PostMapping("/live/start")
    public ReturnResult<LiveHandle> startLive(@RequestParam String deviceId,
                                              @RequestParam int channel,
                                              @RequestParam(required = false, defaultValue = "hls") String protocol,
                                              @RequestParam(required = false, defaultValue = "main") String streamType) {
        DeviceSession session = requireOnline(deviceId);
        LiveHandle handle = session.startLive(channel, protocol, streamType);
        if (handle == null || handle.streamId() == null || handle.streamId().isEmpty()) {
            throw new RuntimeException("设备 " + deviceId + " 未开启直播会话");
        }
        log.info("设备直播已开启: deviceId={}, channel={}, streamId={}", deviceId, channel, handle.streamId());
        return ReturnResult.ok(handle);
    }

    /**
     * 停止直播：释放开启直播时拿到的会话句柄。
     *
     * @param deviceId 设备标识
     * @param streamId 开启直播时返回的会话标识
     * @return 固定为 true；不支持显式停止的厂商会话按空操作处理
     */
    @PostMapping("/live/stop")
    public ReturnResult<Boolean> stopLive(@RequestParam String deviceId,
                                          @RequestParam String streamId) {
        DeviceSession session = requireOnline(deviceId);
        session.stopLive(streamId);
        log.info("设备直播已停止: deviceId={}, streamId={}", deviceId, streamId);
        return ReturnResult.ok(true);
    }

    /**
     * 查询录像列表
     *
     * @param deviceId 设备 标识
     * @param channel  通道号
     * @param start    开始时间（yyyy-MM-dd HH:mm:ss）
     * @param end      结束时间（yyyy-MM-dd HH:mm:ss）
     * @return 录像列表
     */
    @GetMapping("/records")
    public ReturnResult<List<RecordFile>> queryRecords(@RequestParam String deviceId,
                                                       @RequestParam int channel,
                                                       @RequestParam String start,
                                                       @RequestParam String end) {
        DeviceSession session = requireOnline(deviceId);
        return ReturnResult.ok(session.queryRecords(channel, parseRange(start, end)));
    }

    /**
     * 获取录像回放 URL（HLS）
     *
     * @param deviceId 设备 标识
     * @param channel  通道号
     * @param start    开始时间
     * @param end      结束时间
     * @return 回放 URL
     */
    @GetMapping("/playback")
    public ReturnResult<String> playback(@RequestParam String deviceId,
                                         @RequestParam int channel,
                                         @RequestParam String start,
                                         @RequestParam String end) {
        DeviceSession session = requireOnline(deviceId);
        TimeRange range = parseRange(start, end);
        // 优先使用 开放api 平台的回放 URL（HLS）
        String url = session.getPlaybackUrl(channel, range);
        if (url == null || url.isEmpty()) {
            throw new RuntimeException("设备 " + deviceId + " 不支持录像回放或未返回回放 URL");
        }
        log.debug("设备回放 URL: deviceId={}, channel={}, url={}", deviceId, channel, mask(url));
        return ReturnResult.ok(url);
    }

    /**
     * 校验设备在线
     *
     * @param deviceId 设备 标识
     * @return 设备会话
     */
    private DeviceSession requireOnline(String deviceId) {
        DeviceSession session = sessionManager.getSession(deviceId);
        if (session == null || !session.isOnline()) {
            // 同步入库的设备可能尚未建立会话：按需重连一次（重连失败仍按离线处理）
            try {
                deviceService.reconnect(deviceId);
            } catch (Exception e) {
                log.warn("设备按需建立会话失败: deviceId={}, message={}", deviceId, e.getMessage());
            }
            session = sessionManager.getSession(deviceId);
        }
        if (session == null || !session.isOnline()) {
            throw new RuntimeException("设备不在线: " + deviceId);
        }
        return session;
    }

    /**
     * 解析时间范围
     *
     * @param start 启动
     * @param end   结束
     * @return 时间范围
     */
    private TimeRange parseRange(String start, String end) {
        LocalDateTime s;
        LocalDateTime e;
        try {
            s = LocalDateTime.parse(start, DTF);
        } catch (Exception ex) {
            s = LocalDateTime.now().minusDays(1);
        }
        try {
            e = LocalDateTime.parse(end, DTF);
        } catch (Exception ex) {
            e = LocalDateTime.now();
        }
        return new TimeRange(s, e);
    }

    /**
     * 拉流 URL 脱敏（隐藏 令牌 / 签名 段）
     *
     * @param url url
     * @return 脱敏后的 URL
     */
    private static String mask(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q) + "?***";
    }
}
