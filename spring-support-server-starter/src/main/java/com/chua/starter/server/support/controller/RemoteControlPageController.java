// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * <p>
 * 此外还生成走平台直连通道的页面（不经 guacd）：
 * {@code /ms/server/remote/rdp-direct.html}（画布 + FreeRDP 帧推送）、
 * {@code /ms/server/remote/serial-direct.html}（xterm + 串口二进制收发）、
 * {@code /ms/server/remote/telnet-direct.html}（xterm + Telnet 原始 TCP 收发）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/remote")
@RequiredArgsConstructor
public class RemoteControlPageController {

    /**
     * 系统配置服务（guacd-客户端 网关动态化——配置表优先）
    */
    private final ServerConfigService configService;

    /**
     * 服务属性（guacd-客户端 回落值）
    */
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
     * 串口 console 直连页面（生成式）
     * <p>
     * 与 {@link #connect} 的区别：不经 guacd，xterm 直接对接
     * {@link ServerConstants#WEBSOCKET_SERIAL_DIRECT_PATH} 端点，收发走二进制帧（UTF-8）。
     * </p>
     *
     * @param hostId     主机 标识（凭据由后端按主机解析）
     * @param identityId 登录身份 标识（可选）
     * @param targetPort 目标端口（可选）
     * @return 串口 console 直连页 HTML
     */
    @GetMapping(path = "/serial-direct.html", produces = MediaType.TEXT_HTML_VALUE)
    public void serialDirectPage(@RequestParam Long hostId,
                                 @RequestParam(required = false) Long identityId,
                                 @RequestParam(required = false) Integer targetPort,
                                 HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(renderSerialDirectPage(hostId, identityId, targetPort));
    }

    /**
     * Telnet console 直连页面（生成式）
     * <p>
     * 与 {@link #connect} 的区别：不经 guacd，xterm 直接对接
     * {@link ServerConstants#WEBSOCKET_TELNET_DIRECT_PATH} 端点，收发走二进制帧（UTF-8）。
     * </p>
     *
     * @param hostId     主机 标识（凭据由后端按主机解析）
     * @param identityId 登录身份 标识（可选）
     * @param targetPort 目标端口（可选）
     * @return Telnet console 直连页 HTML
     */
    @GetMapping(path = "/telnet-direct.html", produces = MediaType.TEXT_HTML_VALUE)
    public void telnetDirectPage(@RequestParam Long hostId,
                                 @RequestParam(required = false) Long identityId,
                                 @RequestParam(required = false) Integer targetPort,
                                 HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(renderTelnetDirectPage(hostId, identityId, targetPort));
    }

    /**
     * RDP 直连桌面页面（生成式）
     * <p>
     * 不经 guacd：canvas 直接对接 {@link ServerConstants#WEBSOCKET_RDP_DIRECT_PATH} 端点，
     * 接收远端推送的 BGRA 帧（32 字节帧头 + 像素），往回传鼠标/键盘事件。
     * </p>
     *
     * @param hostId     主机 标识（凭据由后端按主机解析）
     * @param identityId 登录身份 标识（可选）
     * @return RDP 直连桌面页 HTML
     */
    @GetMapping(path = "/rdp-direct.html", produces = MediaType.TEXT_HTML_VALUE)
    public void rdpDirectPage(@RequestParam Long hostId,
                              @RequestParam(required = false) Long identityId,
                              HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(renderRdpDirectPage(hostId, identityId));
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
                    // 远控工具条下发的特殊按键（Ctrl+Alt+Del / Alt+Tab / Win 等本机 OS 会截获的组合）：
                    // 先按顺序全部按下，再逆序全部松开。
                    window.addEventListener('message', (e) => {
                      if (e.origin !== location.origin) {
                        return;
                      }
                      const d = e.data;
                      if (!d || d.source !== 'sc-remote' || d.type !== 'send-special-key') {
                        return;
                      }
                      const keys = Array.isArray(d.keys) ? d.keys : [];
                      if (!keys.length) {
                        return;
                      }
                      keys.forEach((k) => client.sendKeyEvent(1, k.keysym));
                      /**
                       * set Timeout
                       * @param ( 参数
                       * @return 返回值
                      */
                      setTimeout(() => {
                        for (let i = keys.length - 1; i >= 0; i--) client.sendKeyEvent(0, keys[i].keysym);
                      }, 60);
                    });
                    // 尺寸
                    const resize = () => client.sendSize(window.innerWidth, window.innerHeight);
                    window.addEventListener('resize', resize);
                    // 延迟到布局完成后再发送初始尺寸
                    setTimeout(resize, 100);
                    setTimeout(resize, 500);
                    // 状态
                    tunnel.onstatechange = (state) => {
                      if (state === Guacamole.Tunnel.State.OPEN) {
                          statusEl.textContent = '已连接';
                      }
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

    /**
     * 渲染串口 console 直连页面（Canvas 风格同 SSH 直连页）
     */
    private String renderSerialDirectPage(Long hostId, Long identityId, Integer targetPort) {
        return """
                <!DOCTYPE html>
                <html lang="zh">
                <head>
                  <meta charset="UTF-8">
                  <title>串口 Console 直连</title>
                  <link rel="stylesheet" href="/term/xterm.css">
                  <style>
                    html, body { margin: 0; height: 100%; background: #111; overflow: hidden; }
                    #term { width: 100%; height: 100%; padding: 4px 0 0 6px; box-sizing: border-box; }
                    #status { position: fixed; top: 8px; right: 14px;
                              color: #eee; background: rgba(0,0,0,.72); padding: 5px 12px; border-radius: 4px;
                              font: 12px/1.4 monospace; z-index: 11; pointer-events: none; }
                    #loading { position: fixed; inset: 0; z-index: 10; display: flex;
                               flex-direction: column; align-items: center; justify-content: center; gap: 14px;
                               background: #111; transition: opacity .25s ease; }
                    #loading.hide { opacity: 0; pointer-events: none; }
                    #loading .lbl { color: #cfd3da; font: 13px/1.5 "Segoe UI", "Microsoft YaHei", sans-serif;
                                    letter-spacing: .3px; text-align: center; }
                    #loading .sub { color: #6b7280; font: 11px/1.4 monospace; }
                    .spinner { width: 38px; height: 38px; border-radius: 50%;
                               border: 3px solid rgba(255,255,255,.14); border-top-color: #6aa1ff;
                               animation: spin .8s linear infinite; }
                    @keyframes spin { to { transform: rotate(360deg); } }
                  </style>
                </head>
                <body>
                  <div id="loading">
                    <div class="spinner"></div>
                    <div class="lbl" id="loading-lbl">正在建立串口连接…</div>
                    <div class="sub" id="loading-sub"></div>
                  </div>
                  <div id="status">正在连接…</div>
                  <div id="term"></div>
                  <script src="/term/xterm.js"></script>
                  <script src="/term/addon-fit.js"></script>
                  <script>
                    (function () {
                      var params = new URLSearchParams(location.search);
                      var hostId = params.get('hostId') || '';
                      var identityId = params.get('identityId') || '';
                      var term = new Terminal({
                        cursorBlink: true, fontFamily: 'Consolas, Menlo, "Courier New", monospace',
                        fontSize: 13, scrollback: 8000,
                        theme: { background: '#111111', foreground: '#e6e6e6' }
                      });
                      var fit = new FitAddon.FitAddon();
                      term.loadAddon(fit);
                      term.open(document.getElementById('term'));
                      fit.fit();
                      var wsProto = location.protocol === 'https:' ? 'wss://' : 'ws://';
                      var wsUrl = wsProto + location.host + '/ws/ms/server/terminal/serial?hostId='
                        + encodeURIComponent(hostId) + '&cols=' + term.cols + '&rows=' + term.rows
                        + (identityId ? '&identityId=' + encodeURIComponent(identityId) : '')
                        + (params.get('x-oauth-token') ? '&x-oauth-token=' + encodeURIComponent(params.get('x-oauth-token')) : '');
                      var ws = new WebSocket(wsUrl);
                      ws.binaryType = 'arraybuffer';
                      var encoder = new TextEncoder();
                      term.onData(function (data) {
                        if (ws.readyState === WebSocket.OPEN) ws.send(encoder.encode(data));
                      });
                      term.onResize(function (size) {
                        if (ws.readyState === WebSocket.OPEN) {
                          ws.send(JSON.stringify({c: 'resize', cols: size.cols, rows: size.rows}));
                        }
                      });
                      var loadingEl = document.getElementById('loading');
                      var loadingLbl = document.getElementById('loading-lbl');
                      var loadingSub = document.getElementById('loading-sub');
                      loadingSub.textContent = 'hostId=' + hostId + (identityId ? (' identityId=' + identityId) : '');
                      function hideLoading() { loadingEl.classList.add('hide'); }
                      var errored = false;
                      ws.onmessage = function (event) {
                        if (typeof event.data === 'string') {
                          var cmd = null;
                          try { cmd = JSON.parse(event.data); } catch (e) { cmd = null; }
                          if (cmd && cmd.c && cmd.c !== 'error' && cmd.c !== 'closed') return;
                          var msg = cmd && cmd.msg ? cmd.msg : event.data;
                          errored = true;
                          document.getElementById('status').textContent = msg;
                          loadingLbl.textContent = msg;
                          loadingLbl.classList.add('err');
                          loadingEl.classList.remove('hide');
                          term.writeln('\\r\\n\\x1b[31m' + msg + '\\x1b[0m');
                          return;
                        }
                        term.write(new Uint8Array(event.data));
                      };
                      ws.onopen = function () {
                        document.getElementById('status').textContent = '已连接（串口直连）';
                        hideLoading();
                        term.focus();
                      };
                      ws.onclose = function () {
                        if (!errored) {
                          document.getElementById('status').textContent = '已断开';
                          loadingLbl.textContent = '连接已关闭，请刷新页面重新连接';
                          loadingLbl.classList.add('err');
                          loadingEl.classList.remove('hide');
                          term.writeln('\\r\\n\\x1b[33m连接已关闭\\x1b[0m');
                        }
                      };
                      ws.onerror = function () {
                        if (!errored) document.getElementById('status').textContent = '连接异常';
                      };
                      function doFit() { try { fit.fit(); } catch (e) { return; } term.focus(); }
                      window.addEventListener('resize', doFit);
                      setTimeout(doFit, 100);
                      setTimeout(doFit, 600);
                      setInterval(function () { if (ws.readyState === WebSocket.OPEN) { try { fit.fit(); } catch (e) {} } }, 2000);
                    })();
                  </script>
                </body>
                </html>
                """;
    }

    /**
     * 渲染 Telnet console 直连页面（与串口页同构，连接端点不同）
     */
    private String renderTelnetDirectPage(Long hostId, Long identityId, Integer targetPort) {
        return """
                <!DOCTYPE html>
                <html lang="zh">
                <head>
                  <meta charset="UTF-8">
                  <title>Telnet 直连</title>
                  <link rel="stylesheet" href="/term/xterm.css">
                  <style>
                    html, body { margin: 0; height: 100%; background: #111; overflow: hidden; }
                    #term { width: 100%; height: 100%; padding: 4px 0 0 6px; box-sizing: border-box; }
                    #status { position: fixed; top: 8px; right: 14px;
                              color: #eee; background: rgba(0,0,0,.72); padding: 5px 12px; border-radius: 4px;
                              font: 12px/1.4 monospace; z-index: 11; pointer-events: none; }
                    #loading { position: fixed; inset: 0; z-index: 10; display: flex;
                               flex-direction: column; align-items: center; justify-content: center; gap: 14px;
                               background: #111; transition: opacity .25s ease; }
                    #loading.hide { opacity: 0; pointer-events: none; }
                    #loading .lbl { color: #cfd3da; font: 13px/1.5 "Segoe UI", "Microsoft YaHei", sans-serif;
                                    letter-spacing: .3px; text-align: center; }
                    #loading .sub { color: #6b7280; font: 11px/1.4 monospace; }
                    .spinner { width: 38px; height: 38px; border-radius: 50%;
                               border: 3px solid rgba(255,255,255,.14); border-top-color: #6aa1ff;
                               animation: spin .8s linear infinite; }
                    @keyframes spin { to { transform: rotate(360deg); } }
                  </style>
                </head>
                <body>
                  <div id="loading">
                    <div class="spinner"></div>
                    <div class="lbl" id="loading-lbl">正在建立 Telnet 连接…</div>
                    <div class="sub" id="loading-sub"></div>
                  </div>
                  <div id="status">正在连接…</div>
                  <div id="term"></div>
                  <script src="/term/xterm.js"></script>
                  <script src="/term/addon-fit.js"></script>
                  <script>
                    (function () {
                      var params = new URLSearchParams(location.search);
                      var hostId = params.get('hostId') || '';
                      var identityId = params.get('identityId') || '';
                      var term = new Terminal({
                        cursorBlink: true, fontFamily: 'Consolas, Menlo, "Courier New", monospace',
                        fontSize: 13, scrollback: 8000,
                        theme: { background: '#111111', foreground: '#e6e6e6' }
                      });
                      var fit = new FitAddon.FitAddon();
                      term.loadAddon(fit);
                      term.open(document.getElementById('term'));
                      fit.fit();
                      var wsProto = location.protocol === 'https:' ? 'wss://' : 'ws://';
                      var wsUrl = wsProto + location.host + '/ws/ms/server/terminal/telnet?hostId='
                        + encodeURIComponent(hostId) + '&cols=' + term.cols + '&rows=' + term.rows
                        + (identityId ? '&identityId=' + encodeURIComponent(identityId) : '')
                        + (targetPort ? '&targetPort=' + encodeURIComponent(String(targetPort)) : '')
                        + (params.get('x-oauth-token') ? '&x-oauth-token=' + encodeURIComponent(params.get('x-oauth-token')) : '');
                      var ws = new WebSocket(wsUrl);
                      ws.binaryType = 'arraybuffer';
                      var encoder = new TextEncoder();
                      term.onData(function (data) {
                        if (ws.readyState === WebSocket.OPEN) ws.send(encoder.encode(data));
                      });
                      term.onResize(function (size) {
                        if (ws.readyState === WebSocket.OPEN) {
                          ws.send(JSON.stringify({c: 'resize', cols: size.cols, rows: size.rows}));
                        }
                      });
                      var loadingEl = document.getElementById('loading');
                      var loadingLbl = document.getElementById('loading-lbl');
                      var loadingSub = document.getElementById('loading-sub');
                      loadingSub.textContent = 'hostId=' + hostId + (identityId ? (' identityId=' + identityId) : '');
                      function hideLoading() { loadingEl.classList.add('hide'); }
                      var errored = false;
                      ws.onmessage = function (event) {
                        if (typeof event.data === 'string') {
                          var cmd = null;
                          try { cmd = JSON.parse(event.data); } catch (e) { cmd = null; }
                          if (cmd && cmd.c && cmd.c !== 'error' && cmd.c !== 'closed') return;
                          var msg = cmd && cmd.msg ? cmd.msg : event.data;
                          errored = true;
                          document.getElementById('status').textContent = msg;
                          loadingLbl.textContent = msg;
                          loadingLbl.classList.add('err');
                          loadingEl.classList.remove('hide');
                          term.writeln('\\r\\n\\x1b[31m' + msg + '\\x1b[0m');
                          return;
                        }
                        term.write(new Uint8Array(event.data));
                      };
                      ws.onopen = function () {
                        document.getElementById('status').textContent = '已连接（Telnet 直连）';
                        hideLoading();
                        term.focus();
                      };
                      ws.onclose = function () {
                        if (!errored) {
                          document.getElementById('status').textContent = '已断开';
                          loadingLbl.textContent = '连接已关闭，请刷新页面重新连接';
                          loadingLbl.classList.add('err');
                          loadingEl.classList.remove('hide');
                          term.writeln('\\r\\n\\x1b[33m连接已关闭\\x1b[0m');
                        }
                      };
                      ws.onerror = function () {
                        if (!errored) document.getElementById('status').textContent = '连接异常';
                      };
                      function doFit() { try { fit.fit(); } catch (e) { return; } term.focus(); }
                      window.addEventListener('resize', doFit);
                      setTimeout(doFit, 100);
                      setTimeout(doFit, 600);
                      setInterval(function () { if (ws.readyState === WebSocket.OPEN) { try { fit.fit(); } catch (e) {} } }, 2000);
                    })();
                  </script>
                </body>
                </html>
                """;
    }

    /**
     * 渲染 RDP 直连桌面页面（原创 / 不依赖 guacamole）
     * <p>
     * canvas 接收远端推送的 BGRA 帧（帧头 32 字节：width/height/stride/bpp/x/y/cx/cy，
     * 其后为每行 stride 字节、自底向上的 BGRA 扫描线），呈像时做坐标翻转；
     * 键鼠事件回传为 JSON 文本帧（{"type":"mouse","x":..,"y":..,"button":..,"down":..}、
     * {"type":"keyboard","keysym":..,"down":..}、{"type":"resize","width":..,"height":..}）。
     * </p>
     */
    private String renderRdpDirectPage(Long hostId, Long identityId) {
        return """
                <!DOCTYPE html>
                <html lang="zh">
                <head>
                  <meta charset="UTF-8">
                  <title>RDP 直连桌面</title>
                  <style>
                    html, body { margin: 0; height: 100%; background: #111; overflow: hidden; }
                    #display { width: 100%; height: 100%; position: relative; }
                    #display canvas { display: block; width: 100%; height: 100%; }
                    #status { position: fixed; top: 8px; right: 14px;
                              color: #eee; background: rgba(0,0,0,.72); padding: 5px 12px; border-radius: 4px;
                              font: 12px/1.4 monospace; z-index: 11; pointer-events: none; }
                    #loading { position: fixed; inset: 0; z-index: 10; display: flex;
                               flex-direction: column; align-items: center; justify-content: center; gap: 14px;
                               background: #111; transition: opacity .25s ease; }
                    #loading.hide { opacity: 0; pointer-events: none; }
                    #loading .lbl { color: #cfd3da; font: 13px/1.5 "Segoe UI", "Microsoft YaHei", sans-serif;
                                    letter-spacing: .3px; text-align: center; }
                    #loading .sub { color: #6b7280; font: 11px/1.4 monospace; }
                    .spinner { width: 38px; height: 38px; border-radius: 50%;
                               border: 3px solid rgba(255,255,255,.14); border-top-color: #6aa1ff;
                               animation: spin .8s linear infinite; }
                    @keyframes spin { to { transform: rotate(360deg); } }
                  </style>
                </head>
                <body>
                  <div id="loading">
                    <div class="spinner"></div>
                    <div class="lbl" id="loading-lbl">正在建立 RDP 连接…</div>
                    <div class="sub" id="loading-sub"></div>
                  </div>
                  <div id="status">正在连接…</div>
                  <div id="display"><canvas></canvas></div>
                  <script>
                    (function () {
                      var params = new URLSearchParams(location.search);
                      var hostId = params.get('hostId') || '';
                      var identityId = params.get('identityId') || '';
                      var displayEl = document.getElementById('display');
                      var canvas = displayEl.querySelector('canvas');
                      var ctx = canvas.getContext('2d');
                      var statusEl = document.getElementById('status');
                      var imgData = null;
                      var naturalW = 0, naturalH = 0;
                      var frameBuf = null;

                      /* 连接建立 */
                      var wsProto = location.protocol === 'https:' ? 'wss://' : 'ws://';
                      var wsUrl = wsProto + location.host + '/ws/ms/server/terminal/rdp?hostId='
                        + encodeURIComponent(hostId)
                        + (identityId ? '&identityId=' + encodeURIComponent(identityId) : '')
                        + ('&width=' + window.innerWidth + '&height=' + window.innerHeight);
                      var ws = new WebSocket(wsUrl);
                      ws.binaryType = 'arraybuffer';

                      var loadingEl = document.getElementById('loading');
                      var loadingLbl = document.getElementById('loading-lbl');
                      var loadingSub = document.getElementById('loading-sub');
                      loadingSub.textContent = 'hostId=' + hostId + (identityId ? (' identityId=' + identityId) : '');
                      function hideLoading() { loadingEl.classList.add('hide'); }

                      /* 帧：写字节数据到 canvas（BGRA，32 字节帧头，自底向上扫描线） */
                      function drawFrame(bytes) {
                        var view = new Uint8Array(bytes);
                        if (view.length < 32) return;
                        var width = view[0] | view[1] << 8 | view[2] << 16 | view[3] << 24;
                        var height = view[4] | view[5] << 8 | view[6] << 16 | view[7] << 24;
                        var stride = view[8] | view[9] << 8 | view[10] << 16 | view[11] << 24;
                        var bpp = view[12] | view[13] << 8 | view[14] << 16 | view[15] << 24;
                        var w = (bpp === 32 || bpp === 4) ? 4 : 3;
                        if (naturalW !== width || naturalH !== height || frameBuf === null || frameBuf.length !== stride * height + 32) {
                          canvas.width = width;
                          canvas.height = height;
                          naturalW = width; naturalH = height;
                          frameBuf = new Uint8Array(stride * height + 32);
                          imgData = ctx.createImageData(width, height);
                        }
                        var data = imgData.data;
                        for (var y = 0; y < height; y++) {
                          var dstY = (height - 1 - y) * width * 4;
                          var srcY = 32 + y * stride;
                          for (var x = 0; x < width; x++) {
                            var s = srcY + x * 4;
                            data[dstY + x * 4] = view[s + 2];
                            data[dstY + x * 4 + 1] = view[s + 1];
                            data[dstY + x * 4 + 2] = view[s];
                            data[dstY + x * 4 + 3] = 255;
                          }
                        }
                        ctx.putImageData(imgData, 0, 0);
                        document.title = width + 'x' + height + ' · ' + canvas.width + 'x' + canvas.height;
                      }

                      /* 键鼠指令 */
                      function send(type, payload) {
                        if (ws.readyState === WebSocket.OPEN) {
                          ws.send(JSON.stringify(Object.assign({type: type}, payload)));
                        }
                      }

                      /* 鼠标：绝对桌面坐标（帧坐标 = 桌面坐标） */
                      function mouseEvent(e) {
                        var rect = canvas.getBoundingClientRect();
                        var x = Math.floor(e.clientX - rect.left);
                        var y = Math.floor(e.clientY - rect.top);
                        if (e.type === 'mousedown' || e.type === 'mousemove') {
                          send('mouse', {x: x, y: y, button: e.type === 'mousedown' ? (e.button + 1) : 0, down: e.type === 'mousedown'});
                          if (e.type === 'mousemove') send('mouse', {x: x, y: y, button: 0, down: false});
                        } else {
                          send('mouse', {x: x, y: y, button: e.button + 1, down: false});
                        }
                      }
                      canvas.addEventListener('mousedown', mouseEvent);
                      canvas.addEventListener('mousemove', mouseEvent);
                      canvas.addEventListener('mouseup', mouseEvent);
                      canvas.addEventListener('click', mouseEvent);
                      canvas.addEventListener('wheel', function (e) {
                        e.preventDefault();
                        send('mouse', {x: 0, y: 0, button: 4, down: e.deltaY > 0});
                      }, {passive: false});

                      /* 键盘：按键码（RDP 扫描码） */
                      document.addEventListener('keydown', function (e) {
                        send('keyboard', {keysym: e.keyCode, down: 1});
                      });
                      document.addEventListener('keyup', function (e) {
                        send('keyboard', {keysym: e.keyCode, down: 0});
                      });

                      /* 窗口尺寸：远端桌面也做缩放（帧循环由框架侧泵送） */
                      var resizeTimer = null;
                      function onResize() {
                        clearTimeout(resizeTimer);
                        resizeTimer = setTimeout(function () {
                          send('resize', {width: window.innerWidth, height: window.innerHeight});
                        }, 200);
                      }
                      window.addEventListener('resize', onResize);

                      ws.onmessage = function (event) {
                        if (event.data instanceof ArrayBuffer) {
                          try { frameBuf = new Uint8Array(event.data); drawFrame(event.data); } catch (e) { return; }
                          return;
                        }
                        var payload = event.data;
                        if (typeof payload === 'string') {
                          try { payload = JSON.parse(payload); } catch (e) { return; }
                          if (payload && payload.c === 'error') {
                            statusEl.textContent = '连接失败: ' + (payload.msg || '未知错误');
                            loadingLbl.textContent = payload.msg || '未知错误';
                            loadingLbl.classList.add('err');
                            loadingEl.classList.remove('hide');
                          } else if (payload && payload.c === 'closed') {
                            statusEl.textContent = '已断开';
                            loadingLbl.textContent = '连接已关闭，请刷新页面重新连接';
                            loadingLbl.classList.add('err');
                            loadingEl.classList.remove('hide');
                          }
                        }
                      };
                      ws.onopen = function () {
                        statusEl.textContent = '已连接（RDP 直连）';
                        hideLoading();
                        canvas.focus();
                        document.title = document.title + ' · 已连接';
                      };
                      ws.onclose = function () {
                        statusEl.textContent = '已断开';
                        loadingLbl.textContent = '连接已关闭，请刷新页面重新连接';
                        loadingLbl.classList.add('err');
                        loadingEl.classList.remove('hide');
                      };
                      ws.onerror = function () {
                        statusEl.textContent = '连接异常';
                      };
                      /* 初始尺寸 */
                      send('resize', {width: window.innerWidth, height: window.innerHeight});
                      setTimeout(onResize, 100);
                      setTimeout(onResize, 500);
                    })();
                  </script>
                </body>
                </html>
                """;
    }
}
