package com.chua.starter.server.support.terminal.constant;

import java.util.Arrays;
import java.util.List;

/**
 * guacd 协议参数常量
 *
 * <p>对齐 Apache Guacamole 官方协议参数定义（同 Orion-Visor 的 GuacdConst）。
 * 参考: https://guacamole.apache.org/doc/gug/configuring-guacamole.html</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface GuacdConst {

    // -------------------- common --------------------

    /** 主机 */
    String HOSTNAME = "hostname";

    /** 端口 */
    String PORT = "port";

    /** 用户名 */
    String USERNAME = "username";

    /** 密码占位符，实际值应从配置 guacd.password 注入 */
    String PASSWORD = "password";

    /** 颜色深度 8/16/24 */
    String COLOR_DEPTH = "color-depth";

    /** 无损压缩 boolean */
    String FORCE_LOSSLESS = "force-lossless";

    /** 屏幕录制文件目录 */
    String RECORDING_PATH = "recording-path";

    /** 自动创建录屏目录 */
    String CREATE_RECORDING_PATH = "create-recording-path";

    // -------------------- rdp --------------------

    /** 域 */
    String DOMAIN = "domain";

    /** 安全模式 any/nla/nla-ext/tls/vmconnect/rdp */
    String SECURITY = "security";

    /** 忽略证书 */
    String IGNORE_CERT = "ignore-cert";

    /** 禁用身份验证 */
    String DISABLE_AUTH = "disable-auth";

    /** 剪贴板规范化 preserve/unix/windows */
    String NORMALIZE_CLIPBOARD = "normalize-clipboard";

    /** 客户端名称 */
    String CLIENT_NAME = "client-name";

    /** 宽度 */
    String WIDTH = "width";

    /** 高度 */
    String HEIGHT = "height";

    /** 分辨率 dpi */
    String DPI = "dpi";

    /** 时区 */
    String TIMEZONE = "timezone";

    /** 控制台会话 */
    String CONSOLE = "console";

    /** 初始化程序 */
    String INITIAL_PROGRAM = "initial-program";

    /** 键盘布局 */
    String SERVER_LAYOUT = "server-layout";

    /** 重置大小 display-update/reconnect */
    String RESIZE_METHOD = "resize-method";

    /** 禁用声音输出 */
    String DISABLE_AUDIO_OUTPUT = "disable-audio";

    /** 启用麦克风 */
    String ENABLE_AUDIO_INPUT = "enable-audio-input";

    /** 启用多点触控 */
    String ENABLE_TOUCH = "enable-touch";

    /** 启用文件传输驱动 */
    String ENABLE_DRIVE = "enable-drive";

    /** 禁用下载 */
    String DISABLE_DOWNLOAD = "disable-download";

    /** 禁用上传 */
    String DISABLE_UPLOAD = "disable-upload";

    /** 驱动名称 */
    String DRIVE_NAME = "drive-name";

    /** 驱动路径 */
    String DRIVE_PATH = "drive-path";

    /** 自动创建驱动路径 */
    String CREATE_DRIVE_PATH = "create-drive-path";

    /** 预连接 id */
    String PRE_CONNECTION_ID = "preconnection-id";

    /** 预连接 blob */
    String PRE_CONNECTION_BLOB = "preconnection-blob";

    /** 启用壁纸 */
    String ENABLE_WALLPAPER = "enable-wallpaper";

    /** 启用主题 */
    String ENABLE_THEMING = "enable-theming";

    /** 启用字体平滑 */
    String ENABLE_FONT_SMOOTHING = "enable-font-smoothing";

    /** 启用窗口拖动 */
    String ENABLE_FULL_WINDOW_DRAG = "enable-full-window-drag";

    /** 启用桌面合成 */
    String ENABLE_DESKTOP_COMPOSITION = "enable-desktop-composition";

    /** 启用菜单动画 */
    String ENABLE_MENU_ANIMATIONS = "enable-menu-animations";

    /** 禁用位图缓存 */
    String DISABLE_BITMAP_CACHING = "disable-bitmap-caching";

    /** 禁用离屏缓存 */
    String DISABLE_OFFSCREEN_CACHING = "disable-offscreen-caching";

    /** 禁用字形缓存 */
    String DISABLE_GLYPH_CACHING = "disable-glyph-caching";

    /** 禁用图形加速 */
    String DISABLE_GFX = "disable-gfx";

    /** 远程应用 */
    String REMOTE_APP = "remote-app";

    /** 远程应用工作目录 */
    String REMOTE_APP_DIR = "remote-app-dir";

    /** 远程应用命令行参数 */
    String REMOTE_APP_ARGS = "remote-app-args";

    // -------------------- ssh --------------------

    /** 私钥 */
    String PRIVATE_KEY = "private-key";

    /** 私钥口令 */
    String PASSPHRASE = "passphrase";

    /** 字体名 */
    String FONT_NAME = "font-name";

    /** 字体大小 */
    String FONT_SIZE = "font-size";

    /** 终端颜色方案 */
    String COLOR_SCHEME = "color-scheme";

    /** 回滚缓冲区 */
    String BACKSPACE = "backspace";

    /** 终端类型 */
    String TERMINAL_TYPE = "terminal-type";

    // -------------------- vnc --------------------

    /** 重试次数 */
    String AUTO_RETRY = "autoretry";

    /** 红蓝交换 */
    String SWAP_RED_BLUE = "swap-red-blue";

    /** 鼠标指针 remote/local */
    String CURSOR = "cursor";

    /** 编码方式 */
    String ENCODINGS = "encodings";

    /** 只读 */
    String READ_ONLY = "read-only";

    /** 剪贴板编码 */
    String CLIPBOARD_ENCODING = "clipboard-encoding";

    /** 压缩等级 */
    String COMPRESS_LEVEL = "compress-level";

    /** 质量等级 */
    String QUALITY_LEVEL = "quality-level";

    /** 是否开启音频 */
    String ENABLE_AUDIO = "enable-audio";

    /** 音频服务器 */
    String AUDIO_SERVERNAME = "audio-servername";

    // -------------------- const --------------------

    String RESIZE_METHOD_DISPLAY_UPDATE = "display-update";

    String RESIZE_METHOD_RECONNECT = "reconnect";

    String DRIVE_DRIVE_NAME = "Drive";

    String SECURITY_VMCONNECT = "vmconnect";

    String SECURITY_ANY = "any";

    String CURSOR_LOCAL = "local";

    List<String> AUDIO_MIMETYPES = Arrays.asList("audio/L8", "audio/L16");
}
