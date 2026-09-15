package com.chua.starter.server.support.controller;

import com.chua.starter.server.support.config.ServerProperties;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.service.ServerConfigService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
* 远程控制页面（生成 URL——前端 iframe 嵌入）
* <p>
* 后端集中生成远程控制页面（内嵌 guacamole-通用-js 客户端 + 现有
* {@code /ws/ms/server/terminal} 隧道端点——guacd / guacd-client 地址均由系统配置表动态提供），
* 前端只需 {@code <iframe :src="/ms/server/remote/connect?hostId=..&protocol=.."/>} 嵌入，
* 不再自己实现 Guacamole 客户端逻辑。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/remote")
@RequiredArgsConstructor
public class RemoteControlPageController {

    /** 系统配置服务（guacd-客户端 网关动态化——配置表优先） */
    private final ServerConfigService configService;

    /** 服务属性（guacd-客户端 回落值） */
    private final ServerProperties properties;

    /**
    * 远程控制页面（返回内嵌 HTML——浏览器/iframe 直接渲染）。
    * <p>
    * 直接写 {@link HttpServletResponse}：本项目定制了消息转换器（无 字符串http消息转换器），
    * 任何 字符串 返回值（裸返回/响应实体）都会 500 "No 转换器 for [类 Java.lang.字符串]"。
    * </p>
    *
    * @param hostId     主机 标识（凭据由后端按主机解析）
    * @param protocol   ssh/vnc/rdp
    * @param identityId 登录身份 标识（可选）
    * @param targetPort 目标端口（可选——空 按协议默认）
    * @return 远程控制页面 HTML
     */
    @GetMapping("/connect")
    public void connect(@RequestParam Long hostId,
                        @RequestParam String protocol,
                        @RequestParam(required = false) Long identityId,
                        @RequestParam(required = false) Integer targetPort,
                        HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(renderPage(hostId, protocol, identityId, targetPort));
    }

    /**
    * 渲染远程控制页面（内嵌 guacamole-通用-js——esm 自包含单文件）。
    * <p>
    * 页面使用 令牌 替换而非 {@code String.formatted}（HTML/CSS 中的 % 会被
    * Formatter 误解析）；guacd-客户端 网关（浏览器回连后端的 WS 隧道地址）
    * 由系统配置表动态提供，空串回落同域 位置.主机。
    * </p>
    * @param hostId 主机标识
    * @param protocol 协议
    * @param identityId identityid
    * @param targetPort Target端口
    * @return renderPage的结果
     */
    private String renderPage(Long hostId, String protocol, Long identityId, Integer targetPort) {
        String idParam = identityId == null ? "" : String.valueOf(identityId);
        String portParam = targetPort == null ? "" : String.valueOf(targetPort);
        // guacd-client 网关：系统配置表优先，空串=同域嵌入（前端 JS 自动用 location.host）
        String clientHost = configService.getString("guacd.client.host", properties.getGuacd().getClientHost());
        if (clientHost == null) {
            clientHost = "";
        }
        clientHost = clientHost.trim().replace("'", "").replace("\"", "");
        return """
                <!DOCTYPE html>
                <html lang="zh">
                <head>
                  <meta charset="UTF-8">
                  <title>远程控制</title>
                  <style>
                    html, body { margin: 0; height: 100%; background: #111; overflow: hidden; }
                    #display { width: 100%; height: 100%; }
                    #display canvas { width: 100% !important; height: 100% !important; }
                    #status { position: fixed; top: 10px; left: 50%; transform: translateX(-50%);
                              color: #fff; background: rgba(0,0,0,.7); padding: 6px 14px; border-radius: 4px;
                              font: 13px monospace; z-index: 9; }
                  </style>
                </head>
                <body>
                  <div id="status">正在连接…</div>
                  <div id="display"></div>
                  <script type="module">
                    import Guacamole from '/guacamole/guacamole-common.min.js';
                    const statusEl = document.getElementById('status');
                    const clientHost = '__CLIENT_HOST__';
                    const wsProto = location.protocol === 'https:' ? 'wss://' : 'ws://';
                    const wsHost = clientHost || location.host;
                    const tunnel = new Guacamole.WebSocketTunnel(
                      wsProto + wsHost + '/ws/ms/server/terminal?hostId=__HOST_ID__&protocol=__PROTOCOL__&identityId=__IDENTITY_ID__&targetPort=__TARGET_PORT__'
                    );
                    const client = new Guacamole.Client(tunnel);
                    const displayEl = client.getDisplay().getElement();
                    displayEl.style.width = '100%';
                    displayEl.style.height = '100%';
                    document.getElementById('display').appendChild(displayEl);
                    client.connect();
                    // 键盘
                    const keyboard = new Guacamole.Keyboard(document);
                    keyboard.onkeydown = (keysym) => client.sendKeyEvent(1, keysym);
                    keyboard.onkeyup = (keysym) => client.sendKeyEvent(0, keysym);
                    // 鼠标
                    const mouse = new Guacamole.Mouse(displayEl);
                    mouse.onmousedown = mouse.onmouseup = (e) => client.sendMouseState(e.state, true);
                    mouse.onmousemove = (e) => client.sendMouseState(e.state, false);
                    mouse.onmousewheel = (e) => client.sendMouseWheel(e.deltaY > 0 ? -1 : 1);
                    // 尺寸
                    const resize = () => client.sendSize(window.innerWidth, window.innerHeight);
                    window.addEventListener('resize', resize);
                    // 延迟到布局完成后再发送初始尺寸
                    setTimeout(resize, 100);
                    setTimeout(resize, 500);
                    // 状态
                    tunnel.onstatechange = (state) => {
                      if (state === Guacamole.Tunnel.State.OPEN) statusEl.textContent = '已连接';
                      else if (state === Guacamole.Tunnel.State.CLOSED) statusEl.textContent = '已断开';
                    };
                    tunnel.onerror = (status) => {
                      statusEl.textContent = '连接失败: ' + (status && status.message ? status.message : '未知错误');
                    };
                  </script>
                </body>
                </html>
                """
                .replace("__CLIENT_HOST__", clientHost)
                .replace("__HOST_ID__", String.valueOf(hostId))
                .replace("__PROTOCOL__", protocol)
                .replace("__IDENTITY_ID__", idParam)
                .replace("__TARGET_PORT__", portParam);
    }
}
