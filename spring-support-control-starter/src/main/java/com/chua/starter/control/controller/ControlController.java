package com.chua.starter.control.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.control.model.ScreenView;
import com.chua.starter.control.model.SessionView;
import com.chua.starter.control.properties.ControlProperties;
import com.chua.webrtc.model.ControlEvent;
import com.chua.webrtc.model.DefaultControlEventFactory;
import com.chua.webrtc.model.WebRtcSession;
import com.chua.webrtc.server.WebRtcServer;
import com.chua.webrtc.spi.media.ScreenCaptureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * WebRTC 远程控制 REST 接口。
 *
 * <p>为前端（{@code @pages/control}）提供设备/会话管理与桌面源查询能力。
 * 真正的媒体流与键鼠控制走 WebSocket 信令 + DataChannel，本接口只做辅助管理：</p>
 * <ul>
 *   <li>{@code GET  /control/sessions} — 活跃会话列表</li>
 *   <li>{@code POST /control/session/create} — 创建会话</li>
 *   <li>{@code DELETE /control/session/destroy/{sessionId}} — 销毁会话</li>
 *   <li>{@code GET  /control/screens} — 桌面采集源列表</li>
 *   <li>{@code GET  /control/config} — 信令地址与配置</li>
 *   <li>{@code POST /control/event/send} — 控制事件 REST 兜底通道</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/control")
@RequiredArgsConstructor
public class ControlController {

    /**
     * WebRTC 服务器
     */
    private final WebRtcServer webRtcServer;

    /**
     * 远程控制配置
     */
    private final ControlProperties properties;

    /**
     * 获取活跃会话。
     *
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public ReturnResult<List<SessionView>> sessions() {
        List<SessionView> views = webRtcServer.sessions().stream()
                .map(this::toSessionView)
                .toList();
        return ReturnResult.ok(views);
    }

    /**
     * 创建远控会话。
     *
     * @param sessionId 会话 ID
     * @return 会话视图
     */
    @PostMapping("/session/create")
    public ReturnResult<SessionView> createSession(@RequestParam String sessionId) {
        WebRtcSession session = webRtcServer.createSession(sessionId);
        log.info("[webrtc] 创建远控会话: {}", sessionId);
        return ReturnResult.ok(toSessionView(session));
    }

    /**
     * 销毁远控会话。
     *
     * @param sessionId 会话 ID
     * @return 空响应
     */
    @DeleteMapping("/session/destroy/{sessionId}")
    public ReturnResult<Void> destroySession(@PathVariable String sessionId) {
        webRtcServer.destroySession(sessionId);
        log.info("[webrtc] 销毁远控会话: {}", sessionId);
        return ReturnResult.ok();
    }

    /**
     * 查询可采集的桌面源。
     *
     * @return 屏幕列表
     */
    @GetMapping("/screens")
    public ReturnResult<List<ScreenView>> screens() {
        try {
            ScreenCaptureService capture = webRtcServer.extensions().screenCaptureService();
            ScreenCaptureService.ScreenInfo[] screens = capture.screens();
            if (screens == null) {
                return ReturnResult.ok(Collections.emptyList());
            }
            List<ScreenView> views = Arrays.stream(screens)
                    .map(s -> new ScreenView(s.id(), s.name(), s.width(), s.height()))
                    .toList();
            return ReturnResult.ok(views);
        } catch (Exception e) {
            log.warn("[webrtc] 枚举桌面源失败: {}", e.getMessage());
            return ReturnResult.ok(Collections.emptyList());
        }
    }

    /**
     * 获取信令服务配置。
     *
     * @return 配置信息
     */
    @GetMapping("/config")
    public ReturnResult<Map<String, Object>> config() {
        return ReturnResult.ok(Map.of(
                "host", properties.getHost(),
                "port", properties.getPort(),
                "path", WebRtcServer.SIGNALING_PATH,
                "wsUrl", "ws://" + properties.getHost() + ":" + properties.getPort() + WebRtcServer.SIGNALING_PATH,
                "frameRate", properties.getFrameRate(),
                "width", properties.getWidth(),
                "height", properties.getHeight()
        ));
    }

    /**
     * 控制事件 REST 兜底通道（DataChannel 不可用时的备用入口）。
     *
     * <p>事件 JSON 与 DataChannel 协议一致（字段平铺在根节点）：
     * {@code {"type":"mouse","path":"control","timestamp":...,"x":..,"y":..,"button":"left","action":"press","delta":0}}</p>
     *
     * @param sessionId 会话 ID
     * @param peerId    连接 ID
     * @param body      控制事件 JSON
     * @return 空响应
     */
    @PostMapping("/event/send")
    public ReturnResult<Void> sendEvent(@RequestParam(defaultValue = "rest") String sessionId,
                                        @RequestParam(defaultValue = "rest") String peerId,
                                        @RequestBody String body) {
        ControlEvent event = new DefaultControlEventFactory().parse(body);
        webRtcServer.extensions().controlService().onControlEvent(sessionId, peerId, event.path(), event);
        return ReturnResult.ok();
    }

    /**
     * 会话 → 视图
     *
     * @param session 会话
     * @return 视图
     */
    private SessionView toSessionView(WebRtcSession session) {
        boolean capturing;
        try {
            capturing = webRtcServer.extensions().screenCaptureService().videoTrack() != null;
        } catch (Exception e) {
            capturing = false;
        }
        return new SessionView(session.getId(), session.peers().size(), capturing);
    }
}
