package com.chua.starter.server.support.terminal;

import com.chua.starter.server.support.service.ServerConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 终端可调参数：guacd 三条协议链路 + 自研 SSH 直连共用的开关。
 *
 * <p><b>为什么集中在一个类里</b>：{@code GuacdConnectionFactory} 里原本有三十多个写死的参数
 * （壁纸、主题、字体平滑、音频、驱动器、VNC 编码与压缩、SSH 终端类型……）。逐个改成
 * {@code configService.getXxx(...)} 会让工厂里散落一堆「读配置 + 兜底」，也无法统一
 * 校验与日志。这里按协议收口，工厂只负责"取生效值并下发"。</p>
 *
 * <p>直连 SSH（{@code SshDirectWebSocketHandler} → MINA PTY）也读这里的
 * {@link #sshTerminalType()}：原来库内把 PTY 类型写死成 {@code vt100}，只有 8 色，
 * 与页面里 256 色的 xterm 不匹配。</p>
 *
 * <p><b>校验策略</b>：这些值会原样进 guacd 的协议参数，写错不是「画质差一点」，
 * 而是<b>连不上</b>（例如色深 20、编码写个不存在的 ZZZ）。所以全部走
 * 「类型/范围/白名单校验，不合格回落默认」，并打一条 warn 说明回落原因 ——
 * 否则用户会以为自己的配置生效了。</p>
 *
 * <p>键名统一前缀：{@code rdp.} / {@code vnc.} / {@code ssh.}，存在 {@code ms_server_config}，
 * 系统配置页可改，连接建立时读取（改完下次连接生效，不用重启）。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuacdTunables {

    /**
     * 默认终端类型：带 256 色，绝大多数工具链都能识别
     */
    public static final String DEFAULT_TERMINAL_TYPE = "xterm-256color";

    /**
     * 终端类型/字体名/键盘布局这类字符串的通用校验：字母数字 + 常见分隔符
     */
    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{1,39}$");

    /**
     * VNC 支持的编码名（guacd 认这些，写错会导致连接直接失败）
     */
    private static final Set<String> VNC_ENCODINGS = Set.of(
            "ZRLE", "Hextile", "Tight", "RRE", "Raw", "CopyRect", "CoRRE", "TRLE", "zlib", "zlibhex", "Zlib");

    /**
     * VNC 光标渲染位置
     */
    private static final Set<String> VNC_CURSOR = Set.of("local", "remote");

    /**
     * 色深允许档位（RDP / VNC 共用）
     */
    private static final Set<Integer> COLOR_DEPTHS = Set.of(8, 16, 24, 32);

    /**
     * 系统配置服务
     */
    private final ServerConfigService configService;

    /* ==================== SSH ==================== */

    /**
     * 终端类型（远端看到的 TERM）。
     *
     * @return 合法取值，默认 {@link #DEFAULT_TERMINAL_TYPE}
     */
    public String sshTerminalType() {
        return text("ssh.terminal-type", DEFAULT_TERMINAL_TYPE);
    }

    /**
     * 终端字体名（guacd 侧渲染，仅当走 guacd 的 SSH 时生效）。
     *
     * @return 字体名
     */
    public String sshFontName() {
        return text("ssh.font-name", "monospace");
    }

    /**
     * 终端字号（guacd 侧渲染）。
     *
     * @return 8..40 之间的值，越界回落 12
     */
    public int sshFontSize() {
        return intIn("ssh.font-size", 12, 8, 40);
    }

    /**
     * 退格键发送的字符：127（DEL）或 8（BS）。
     *
     * @return 127 或 8，其它回落 127
     */
    public int sshBackspace() {
        int raw = configService.getInt("ssh.backspace", 127);
        if (raw != 127 && raw != 8) {
            log.warn("[terminal] 配置 ssh.backspace = {} 只支持 127/8，已回落到 127", raw);
            return 127;
        }
        return raw;
    }

    /* ==================== VNC ==================== */

    /**
     * VNC 色深。
     *
     * @return 8/16/24/32，其它回落 24
     */
    public int vncColorDepth() {
        return colorDepth("vnc.color-depth", 24);
    }

    /**
     * VNC 压缩级别（0 不压缩、9 最大压缩）。
     *
     * @return 0..9，越界回落 7
     */
    public int vncCompressLevel() {
        return intIn("vnc.compress-level", 7, 0, 9);
    }

    /**
     * VNC 画质级别（0 最省带宽、9 最好画质）。
     *
     * @return 0..9，越界回落 6
     */
    public int vncQualityLevel() {
        return intIn("vnc.quality-level", 6, 0, 9);
    }

    /**
     * 是否交换红蓝通道（部分 VNC 服务端颜色反转时需要）。
     *
     * @return 需要交换返回 true
     */
    public boolean vncSwapRedBlue() {
        return bool("vnc.swap-red-blue", false);
    }

    /**
     * 光标由谁绘制：{@code local} 本地合成（跟手）/ {@code remote} 远端下发（形状准确）。
     *
     * @return local 或 remote，其它回落 local
     */
    public String vncCursor() {
        return oneOf("vnc.cursor", "local", VNC_CURSOR);
    }

    /**
     * VNC 编码优先级列表（逗号分隔）。
     *
     * <p>会逐个校验编码名：只要有一个不认识就整体回落默认值——半个合法的列表会让
     * guacd 协商出意外结果，比干脆用默认更难查。</p>
     *
     * @return 合法编码列表
     */
    public String vncEncodings() {
        String def = "ZRLE,Hextile,Tight";
        String raw = configService.getString("vnc.encodings", def);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        Set<String> picked = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            String matched = VNC_ENCODINGS.stream()
                    .filter(allowed -> allowed.equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                log.warn("[terminal] 配置 vnc.encodings 含未知编码 {}，整份回落到 {}", name, def);
                return def;
            }
            picked.add(matched);
        }
        return picked.isEmpty() ? def : String.join(",", picked);
    }

    /**
     * VNC 断线自动重连次数。
     *
     * @return 0..10，越界回落 0
     */
    public int vncAutoRetry() {
        return intIn("vnc.auto-retry", 0, 0, 10);
    }

    /* ==================== RDP 体验 ==================== */

    /**
     * RDP DPI（缩放比例相关，Windows 会按它放大界面）。
     *
     * @return 60..480，越界回落 96
     */
    public int rdpDpi() {
        return intIn("rdp.dpi", 96, 60, 480);
    }

    /**
     * 是否允许远端显示壁纸（关掉更省带宽）。
     *
     * @return 允许返回 true
     */
    public boolean rdpWallpaper() {
        return bool("rdp.wallpaper", false);
    }

    /**
     * 是否允许远端主题（关掉更省带宽）。
     *
     * @return 允许返回 true
     */
    public boolean rdpTheming() {
        return bool("rdp.theming", false);
    }

    /**
     * 是否启用字体平滑（开更清晰，略占带宽）。
     *
     * @return 启用返回 true
     */
    public boolean rdpFontSmoothing() {
        return bool("rdp.font-smoothing", true);
    }

    /**
     * 是否允许整窗拖拽（关掉只显示窗口轮廓，弱网更流畅）。
     *
     * @return 允许返回 true
     */
    public boolean rdpFullWindowDrag() {
        return bool("rdp.full-window-drag", true);
    }

    /**
     * 是否启用桌面合成（Aero 效果）。
     *
     * @return 启用返回 true
     */
    public boolean rdpDesktopComposition() {
        return bool("rdp.desktop-composition", true);
    }

    /**
     * 是否播放菜单动画。
     *
     * @return 播放返回 true
     */
    public boolean rdpMenuAnimations() {
        return bool("rdp.menu-animations", false);
    }

    /**
     * 是否允许音频输入（麦克风）。
     *
     * @return 允许返回 true
     */
    public boolean rdpAudioInput() {
        return bool("rdp.audio-input", false);
    }

    /**
     * 是否允许音频输出。
     *
     * @return 允许返回 true
     */
    public boolean rdpAudioOutput() {
        return bool("rdp.audio-output", true);
    }

    /**
     * 是否开启驱动器映射（远程桌面里的文件传输通道）。
     *
     * @return 开启返回 true
     */
    public boolean rdpDrive() {
        return bool("rdp.drive", false);
    }

    /**
     * 是否以控制台会话接入（多会话/已登录不踢人的场景用）。
     *
     * @return 控制台模式返回 true
     */
    public boolean rdpConsole() {
        return bool("rdp.console", false);
    }

    /**
     * 是否忽略服务端证书错误（自签证书场景需要）。
     *
     * @return 忽略返回 true
     */
    public boolean rdpIgnoreCert() {
        return bool("rdp.ignore-cert", true);
    }

    /**
     * RDP 会话时区（远端据此显示时间）。
     *
     * @return 合法时区名，默认 Asia/Shanghai
     */
    public String rdpTimezone() {
        return text("rdp.timezone", "Asia/Shanghai");
    }

    /**
     * RDP 键盘布局。
     *
     * @return 合法布局名，默认 en-us-qwerty
     */
    public String rdpKeyboardLayout() {
        return text("rdp.keyboard-layout", "en-us-qwerty");
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 读布尔配置。
     *
     * @param key      配置键
     * @param defValue 默认值
     * @return 生效值
     */
    private boolean bool(String key, boolean defValue) {
        return configService.getBoolean(key, defValue);
    }

    /**
     * 读整数配置并校验范围，越界回落默认值。
     *
     * @param key      配置键
     * @param defValue 默认值（同时作为越界回落值）
     * @param min      最小值
     * @param max      最大值
     * @return 生效值
     */
    private int intIn(String key, int defValue, int min, int max) {
        int raw = configService.getInt(key, defValue);
        if (raw < min || raw > max) {
            log.warn("[terminal] 配置 {} = {} 超出 {}..{}，已回落到 {}", key, raw, min, max, defValue);
            return defValue;
        }
        return raw;
    }

    /**
     * 读色深配置（只允许 8/16/24/32）。
     *
     * @param key      配置键
     * @param defValue 默认值
     * @return 生效值
     */
    private int colorDepth(String key, int defValue) {
        int raw = configService.getInt(key, defValue);
        if (!COLOR_DEPTHS.contains(raw)) {
            log.warn("[terminal] 配置 {} = {} 不是合法色深（8/16/24/32），已回落到 {}", key, raw, defValue);
            return defValue;
        }
        return raw;
    }

    /**
     * 读枚举字符串配置（大小写不敏感）。
     *
     * @param key      配置键
     * @param defValue 默认值
     * @param allowed  允许的取值
     * @return 生效值（统一小写）
     */
    private String oneOf(String key, String defValue, Set<String> allowed) {
        String raw = configService.getString(key, defValue);
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(value) ? value : defValue;
    }

    /**
     * 读一个「安全 token」字符串（终端类型、字体名、时区、键盘布局）。
     *
     * @param key      配置键
     * @param defValue 默认值
     * @return 合法值，非法时回落默认
     */
    private String text(String key, String defValue) {
        String raw = configService.getString(key, defValue);
        String value = raw == null ? "" : raw.trim();
        if (!SAFE_TOKEN.matcher(value).matches()) {
            log.warn("[terminal] 配置 {} = {} 不是合法的标识（字母数字与 ._:- 组成，2..40 字符），已回落到 {}",
                    key, value, defValue);
            return defValue;
        }
        return value;
    }
}
