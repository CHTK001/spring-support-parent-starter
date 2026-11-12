// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.terminal;

import com.chua.starter.server.support.config.ServerProperties;
import com.chua.starter.server.support.enums.ProtocolType;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerConfigService;
import com.chua.starter.server.support.terminal.constant.GuacdConst;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * guacd 连接工厂（对齐 Orion-Visor 的 guacdtunnel 参数集）
 *
 * <p>按协议（SSH/VNC/RDP）组装 {@link GuacamoleConfiguration} 并与 guacd 完成握手。
 * 协议参数遵循 Guacamole 官方协议定义，支持：
 * <ul>
 *   <li>SSH：私钥/密码两种认证，终端类型/字体/颜色方案</li>
 *   <li>RDP：磁盘映射、RemoteApp、音频、时区、图形优化、预连接(Hyper-V)</li>
 *   <li>VNC：红蓝交换、压缩/质量、编码、剪贴板、音频</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuacdConnectionFactory {

    /**
     * SSH 默认端口
    */
    private static final int DEFAULT_SSH_PORT = 22;

    /**
     * RDP 默认端口
    */
    private static final int DEFAULT_RDP_PORT = 3389;

    /**
     * VNC 默认端口
    */
    private static final int DEFAULT_VNC_PORT = 5900;

    /**
     * 服务属性（guacd 地址）
    */
    private final ServerProperties properties;

    /**
     * 系统配置服务（guacd 地址动态化——配置表优先）
    */
    private final ServerConfigService configService;

    /**
     * RDP 显示参数（分辨率/色深/画质，系统配置可改，带范围校验与回落）
     */
    private final RdpDisplaySettings rdpDisplay;

    /**
     * guacd 三条链路的可调开关（体验/设备/VNC/SSH 外观），全部带校验与回落
     */
    private final GuacdTunables tunables;

    /**
     * 创建与 guacd 完成握手的隧道
     *
     * @param protocolType 协议类型
     * @param address      目标主机地址
     * @param targetPort   目标服务端口，空 时按协议默认值
     * @param credential   解密后的凭据
     * @param sessionId    会话标识
     * @return 已配置隧道（使用方负责 关闭）
     */
    public GuacdTunnel create(ProtocolType protocolType, String address,
                              Integer targetPort, SshCredential credential, String sessionId) {
        var guacd = properties.getGuacd();
        // guacd 地址动态化：系统配置表优先（运行时可在系统配置页修改），无配置回落 application.yml
        String host = configService.getString("guacd.host", guacd.getHost());
        int port = configService.getInt("guacd.port", guacd.getPort());
        GuacdTunnel tunnel = new GuacdTunnel(protocolType.name().toLowerCase(), sessionId,
                host, port);
        switch (protocolType) {
            // SSH 终端：支持密码或私钥两种认证
            case SSH -> buildSshConfig(tunnel, address, targetPort, credential);
            // VNC 桌面：口令认证 + 图形参数
            case VNC -> buildVncConfig(tunnel, address, targetPort, credential);
            // RDP 远程桌面：账号密码 + 磁盘映射 + 图形参数
            case RDP -> buildRdpConfig(tunnel, address, targetPort, credential);
            default -> throw new IllegalArgumentException("协议不支持经 guacd 连接: " + protocolType);
        }
        try {
            tunnel.connect();
        } catch (Exception e) {
            tunnel.close();
            /**
             * runtime异常。
             * @param e.getMessage( e.获取消息(
             * @return runtime异常的结果
             */
            throw new RuntimeException(e.getMessage(), e);
        }
        return tunnel;
    }

    /**
     * 组装 SSH 参数
     * @param tunnel tunnel
     * @param address 地址
     * @param targetPort Target端口
     * @param credential credential
     */
    private void buildSshConfig(GuacdTunnel tunnel, String address, Integer targetPort, SshCredential credential) {
        tunnel.remote(address, targetPort == null ? DEFAULT_SSH_PORT : targetPort);
        if (notBlank(credential.username())) {
            tunnel.setParameter(GuacdConst.USERNAME, credential.username());
        }
        if (credential.hasKey()) {
            // 私钥内容直接经协议下发，由 guacd 完成认证
            tunnel.setParameter(GuacdConst.PRIVATE_KEY, credential.privateKey());
            if (notBlank(credential.password())) {
                tunnel.setParameter(GuacdConst.PASSPHRASE, credential.password());
            }
        } else if (notBlank(credential.password())) {
            tunnel.setParameter(GuacdConst.PASSWORD, credential.password());
        }
        // 终端外观：终端类型/字体/字号/退格都可在系统配置里改（带合法性校验与回落）
        tunnel.setParameter(GuacdConst.COLOR_SCHEME, "gray-black");
        tunnel.setParameter(GuacdConst.FONT_NAME, tunables.sshFontName());
        tunnel.setParameter(GuacdConst.FONT_SIZE, tunables.sshFontSize());
        tunnel.setParameter(GuacdConst.TERMINAL_TYPE, tunables.sshTerminalType());
        tunnel.setParameter(GuacdConst.BACKSPACE, tunables.sshBackspace());
    }

    /**
     * 组装 VNC 参数
     * @param tunnel tunnel
     * @param address 地址
     * @param targetPort Target端口
     * @param credential credential
     */
    private void buildVncConfig(GuacdTunnel tunnel, String address, Integer targetPort, SshCredential credential) {
        tunnel.remote(address, targetPort == null ? DEFAULT_VNC_PORT : targetPort);
        if (notBlank(credential.password())) {
            tunnel.setParameter(GuacdConst.PASSWORD, credential.password());
        }
        // 图形参数：色深/压缩/画质/编码/光标都可调（vnc.* 键，带白名单校验与回落）
        tunnel.setParameter(GuacdConst.COLOR_DEPTH, tunables.vncColorDepth());
        tunnel.setParameter(GuacdConst.COMPRESS_LEVEL, tunables.vncCompressLevel());
        tunnel.setParameter(GuacdConst.QUALITY_LEVEL, tunables.vncQualityLevel());
        tunnel.setParameter(GuacdConst.SWAP_RED_BLUE, tunables.vncSwapRedBlue());
        tunnel.setParameter(GuacdConst.CURSOR, tunables.vncCursor());
        tunnel.setParameter(GuacdConst.ENCODINGS, tunables.vncEncodings());
        tunnel.setParameter(GuacdConst.CLIPBOARD_ENCODING, GuacdConst.CLIPBOARD_ENCODING);
        tunnel.setParameter(GuacdConst.AUTO_RETRY, tunables.vncAutoRetry());
    }

    /**
     * 组装 RDP 参数
     * @param tunnel tunnel
     * @param address 地址
     * @param targetPort Target端口
     * @param credential credential
     */
    private void buildRdpConfig(GuacdTunnel tunnel, String address, Integer targetPort, SshCredential credential) {
        tunnel.remote(address, targetPort == null ? DEFAULT_RDP_PORT : targetPort);
        if (notBlank(credential.username())) {
            tunnel.setParameter(GuacdConst.USERNAME, credential.username());
        }
        if (notBlank(credential.password())) {
            tunnel.setParameter(GuacdConst.PASSWORD, credential.password());
        }
        // 显式空域名，防止 guacd 自动用 Windows 机器名作为域前缀（如 SAGE4\administrator）
        tunnel.setParameter("domain", "");
        // 分辨率：系统配置 rdp.width / rdp.height（越界回落默认，见 RdpDisplaySettings）
        tunnel.size(rdpDisplay.width(), rdpDisplay.height());
        tunnel.dpi(tunables.rdpDpi());
        // 安全与认证：默认强制 NLA（Windows 默认要求），可用系统配置 guacd.rdp.security 覆盖
        // （取值 any/rdp/tls/nla/nla-ext/vmconnect，部分 RDP 服务/网关需非 NLA 模式）
        String rdpSecurity = configService.getString("guacd.rdp.security", "nla");
        tunnel.setParameter(GuacdConst.SECURITY, rdpSecurity);
        tunnel.setParameter(GuacdConst.IGNORE_CERT, tunables.rdpIgnoreCert());
        tunnel.setParameter(GuacdConst.DISABLE_AUTH, false);
        // 剪贴板/时区/布局
        tunnel.setParameter(GuacdConst.NORMALIZE_CLIPBOARD, "preserve");
        tunnel.setParameter(GuacdConst.TIMEZONE, tunables.rdpTimezone());
        tunnel.setParameter(GuacdConst.SERVER_LAYOUT, tunables.rdpKeyboardLayout());
        // 自适应窗口
        tunnel.setParameter(GuacdConst.RESIZE_METHOD, GuacdConst.RESIZE_METHOD_DISPLAY_UPDATE);
        // 图形优化
        tunnel.setParameter(GuacdConst.COLOR_DEPTH, rdpDisplay.colorDepth());
        tunnel.setParameter(GuacdConst.FORCE_LOSSLESS, rdpDisplay.forceLossless());
        tunnel.setParameter(GuacdConst.ENABLE_WALLPAPER, tunables.rdpWallpaper());
        tunnel.setParameter(GuacdConst.ENABLE_THEMING, tunables.rdpTheming());
        tunnel.setParameter(GuacdConst.ENABLE_FONT_SMOOTHING, tunables.rdpFontSmoothing());
        tunnel.setParameter(GuacdConst.ENABLE_FULL_WINDOW_DRAG, tunables.rdpFullWindowDrag());
        tunnel.setParameter(GuacdConst.ENABLE_DESKTOP_COMPOSITION, tunables.rdpDesktopComposition());
        tunnel.setParameter(GuacdConst.ENABLE_MENU_ANIMATIONS, tunables.rdpMenuAnimations());
        tunnel.setParameter(GuacdConst.DISABLE_BITMAP_CACHING, false);
        tunnel.setParameter(GuacdConst.DISABLE_OFFSCREEN_CACHING, false);
        tunnel.setParameter(GuacdConst.DISABLE_GLYPH_CACHING, false);
        tunnel.setParameter(GuacdConst.DISABLE_GFX, false);
        // 音频：输出默认开、输入（麦克风）默认关；DISABLE_AUDIO_OUTPUT 是反向参数
        tunnel.setAudioMimeTypes(GuacdConst.AUDIO_MIMETYPES);
        tunnel.setParameter(GuacdConst.ENABLE_AUDIO_INPUT, tunables.rdpAudioInput());
        tunnel.setParameter(GuacdConst.DISABLE_AUDIO_OUTPUT, !tunables.rdpAudioOutput());
        // 客户端标识 + RDP 磁盘映射（文件传输驱动）
        tunnel.setParameter(GuacdConst.CLIENT_NAME, "spring-support");
        tunnel.setParameter(GuacdConst.ENABLE_DRIVE, tunables.rdpDrive());
        tunnel.setParameter(GuacdConst.CREATE_DRIVE_PATH, false);
        tunnel.setParameter(GuacdConst.DRIVE_NAME, GuacdConst.DRIVE_DRIVE_NAME);
        String drivePath = properties.getGuacd().getDrivePath();
        if (notBlank(drivePath)) {
            tunnel.setParameter(GuacdConst.DRIVE_PATH, drivePath);
        }
        // 触控/控制台
        tunnel.setParameter(GuacdConst.ENABLE_TOUCH, false);
        tunnel.setParameter(GuacdConst.CONSOLE, tunables.rdpConsole());
    }

    /**
     * 非空判断
     *
     * @param value 待判断字符串
     * @return 是否非空
     */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}