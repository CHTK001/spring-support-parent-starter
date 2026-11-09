package com.chua.nativerdp.support;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RDP 直连原生桥（rdpj.dll + FreeRDP 3.x 动态库）的 JNA 绑定。
 * <p>
 * 运行时按以下顺序寻找原生库：
 * <ol>
 *     <li>系统属性 {@code chua.rdpj.dir} 或环境变量 {@code CHUA_RDPJ_DIR} 指定的目录；</li>
 *     <li>classpath 资源 {@code native/rdpj/win32-x86-64/} 内的 DLL（解包到临时目录）。</li>
 * </ol>
 * 随包分发的 {@code rdpj.dll} 已静态链接 FreeRDP / WinPR / OpenSSL / zlib，
 * 只依赖 Windows 系统库，因此清单里只有它一个文件；解包目录仍会加入
 * Windows DLL 搜索目录，方便外部替换版原生库时带上自己的依赖。
 * </p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class RdpjBridge {

    /**
     * 原生库所在目录的覆盖属性
     */
    public static final String DIR_PROPERTY = "chua.rdpj.dir";

    /**
     * 原生库所在目录的覆盖环境变量
     */
    public static final String DIR_ENV = "CHUA_RDPJ_DIR";

    /**
     * 桥接库文件名
     */
    private static final String LIBRARY_NAME = "rdpj.dll";

    /**
     * 资源清单文件名（逐行列出 DLL 文件名）
     */
    private static final String INDEX_NAME = "rdpj.index";

    /**
     * 资源根目录
     */
    private static final String RESOURCE_ROOT = "/native/rdpj/win32-x86-64/";

    /**
     * 单例（延迟初始化）
     */
    private static volatile Rdpj INSTANCE;

    /**
     * 最近一次加载失败原因
     */
    private static volatile String loadError;

    private RdpjBridge() {
    }

    /**
     * 获取原生桥实例
     *
     * @return 原生桥接口
     * @throws IllegalStateException 原生库缺失或加载失败
     */
    public static Rdpj instance() {
        Rdpj rdpj = INSTANCE;
        if (rdpj != null) {
            return rdpj;
        }
        synchronized (RdpjBridge.class) {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            try {
                INSTANCE = load();
                loadError = null;
            } catch (Throwable e) {
                loadError = describe(e);
                throw new IllegalStateException("RDP 原生桥加载失败 - " + loadError, e);
            }
            return INSTANCE;
        }
    }

    /**
     * 原生桥是否可用（不抛异常，供能力探测）
     *
     * @return true 表示已加载
     */
    public static boolean available() {
        try {
            instance();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 最近一次加载失败原因
     *
     * @return 失败描述，可用时为 null
     */
    public static String loadError() {
        return loadError;
    }

    /**
     * 加载原生库
     *
     * @return 原生桥接口
     */
    private static Rdpj load() throws IOException {
        File dir = resolveDir();
        File library = new File(dir, LIBRARY_NAME);
        if (!library.isFile()) {
            throw new IOException("未找到 " + library.getAbsolutePath());
        }
        // 把原生库目录加入 DLL 搜索路径，供 FreeRDP 依赖解析
        Kernel32.INSTANCE.SetDllDirectoryW(new WString(dir.getAbsolutePath()));
        Map<String, Object> options = new HashMap<>(4);
        options.put(Library.OPTION_STRING_ENCODING, "UTF-8");
        options.put(Library.OPTION_ALLOW_OBJECTS, Boolean.TRUE);
        return Native.load(library.getAbsolutePath(), Rdpj.class, options);
    }

    /**
     * 解析原生库目录（外部目录优先，否则解包 classpath 资源）
     *
     * @return 原生库目录
     */
    private static File resolveDir() throws IOException {
        String external = System.getProperty(DIR_PROPERTY);
        if (external == null || external.trim().isEmpty()) {
            external = System.getenv(DIR_ENV);
        }
        if (external != null && !external.trim().isEmpty()) {
            File dir = new File(external.trim());
            if (dir.isDirectory()) {
                return dir;
            }
            throw new IOException("原生库目录不存在: " + dir.getAbsolutePath());
        }
        return extractResources();
    }

    /**
     * 解包 classpath 中的原生库到临时目录（幂等）
     *
     * @return 解包目录
     */
    private static File extractResources() throws IOException {
        File base = new File(System.getProperty("java.io.tmpdir"), "chua-rdpj");
        File dir = new File(base, "win32-x86-64");
        List<String> names = readIndex();
        if (names.isEmpty()) {
            throw new IOException("缺少原生库资源清单 " + RESOURCE_ROOT + INDEX_NAME);
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("无法创建原生库目录: " + dir.getAbsolutePath());
        }
        for (String name : names) {
            File target = new File(dir, name);
            try (InputStream in = RdpjBridge.class.getResourceAsStream(RESOURCE_ROOT + name)) {
                if (in == null) {
                    throw new IOException("缺少原生库资源 " + RESOURCE_ROOT + name);
                }
                // 已存在且大小一致时跳过，避免重复写
                if (target.isFile() && target.length() == resourceSize(in)) {
                    continue;
                }
                Path tmp = Files.createTempFile(dir.toPath(), name, ".tmp");
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return dir;
    }

    /**
     * 读取资源清单
     *
     * @return DLL 文件名列表
     */
    private static List<String> readIndex() throws IOException {
        List<String> names = new ArrayList<>();
        try (InputStream in = RdpjBridge.class.getResourceAsStream(RESOURCE_ROOT + INDEX_NAME)) {
            if (in == null) {
                return names;
            }
            byte[] buffer = new byte[8192];
            StringBuilder text = new StringBuilder();
            int read;
            while ((read = in.read(buffer)) > 0) {
                text.append(new String(buffer, 0, read, "UTF-8"));
            }
            for (String line : text.toString().split("\\R")) {
                String name = line.trim();
                if (!name.isEmpty() && !name.startsWith("#")) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * 探测资源长度（用可用字节数近似，仅用于跳过重复解包）
     *
     * @param in 输入流
     * @return 可用字节数
     */
    private static long resourceSize(InputStream in) {
        try {
            return in.available();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * 异常描述
     *
     * @param e 异常
     * @return 描述文本
     */
    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    /**
     * kernel32 最小绑定（仅用于设置 DLL 搜索目录）
     */
    private interface Kernel32 extends Library {

        /**
         * 单例
         */
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        /**
         * 替换 DLL 搜索目录
         *
         * @param path 目录
         * @return 成功标志
         */
        boolean SetDllDirectoryW(WString path);
    }

    /**
     * rdpj.dll 导出的 C 函数。
     * <p>
     * 约定：句柄为不透明指针；所有返回 int 的函数 0 表示成功，负值表示失败；
     * 帧数据由 {@link #rdpj_last_frame} 与 {@link #rdpj_frame} 组合取得。
     * </p>
     */
    public interface Rdpj extends Library {

        /**
         * 创建 RDP 实例（未连接）
         *
         * @return 句柄，失败返回 null
         */
        Pointer rdpj_create();

        /**
         * 发起连接（异步，立即返回；用 {@link #rdpj_state} 轮询结果）
         *
         * @param handle   句柄
         * @param host     主机地址
         * @param port     端口
         * @param user     用户名
         * @param password 口令
         * @param locale   区域（如 zh-CN）
         * @return 0 表示已启动
         */
        int rdpj_connect(Pointer handle, String host, int port, String user, String password, String locale);

        /**
         * 连接状态：0 连接中，1 已连接，2 已关闭，-1 失败
         *
         * @param handle 句柄
         * @return 状态
         */
        int rdpj_state(Pointer handle);

        /**
         * 最近一次错误描述（UTF-8）
         *
         * @param handle 句柄
         * @return 错误描述
         */
        String rdpj_last_error(Pointer handle);

        /**
         * 帧头指针（8 个 int：width,height,stride,bpp,x,y,cx,cy）
         *
         * @param handle 句柄
         * @return 帧头指针，未出帧时为 null
         */
        Pointer rdpj_last_frame(Pointer handle);

        /**
         * 取增量帧：帧头 32 字节 + 脏行像素
         *
         * @param handle 句柄
         * @param buffer 输出缓冲
         * @param size   缓冲长度
         * @param outLen 实际写入长度
         * @return 写入字节数，无新帧为 0
         */
        int rdpj_frame(Pointer handle, Pointer buffer, int size, IntByReference outLen);

        /**
         * 投递鼠标事件
         *
         * @param handle 句柄
         * @param x      坐标 X
         * @param y      坐标 Y
         * @param button 1 左键 2 右键 3 中键 0 仅移动
         * @param down   按下标志
         * @return 0 表示已入队
         */
        int rdpj_mouse(Pointer handle, int x, int y, int button, int down);

        /**
         * 投递键盘事件
         *
         * @param handle 句柄
         * @param code   RDP scancode（高位 0x100 表示扩展键）
         * @param down   按下标志
         * @return 0 表示已入队
         */
        int rdpj_key(Pointer handle, short code, int down);

        /**
         * 投递滚轮事件
         *
         * @param handle 句柄
         * @param x      坐标 X
         * @param y      坐标 Y
         * @param delta  滚动量（正数向上，负数向下）
         * @return 0 表示已入队
         */
        int rdpj_wheel(Pointer handle, int x, int y, int delta);

        /**
         * 请求桌面尺寸（当前版本忽略，返回 0）
         *
         * @param handle 句柄
         * @param width  宽度
         * @param height 高度
         * @return 0
         */
        int rdpj_resize(Pointer handle, int width, int height);

        /**
         * 关闭连接（保留句柄，可再次 destroy）
         *
         * @param handle 句柄
         */
        void rdpj_close(Pointer handle);

        /**
         * 释放句柄
         *
         * @param handle 句柄
         */
        void rdpj_destroy(Pointer handle);
    }
}
