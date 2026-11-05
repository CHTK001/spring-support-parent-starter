// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 子进程输出读取与解码
 *
 * <p>cmd.exe 的内置命令把消息写进重定向管道时用的是系统 ANSI 代码页，简体中文机器上是 CP936；
 * {@code chcp 65001} 改的只是控制台代码页，对没有控制台的子进程无效（实测 chcp 回显 65001、
 * 而 {@code echo 测试汉字} 仍输出 GBK 字节）。一律按 UTF-8 读就会把中文回显解成一串替换字符，
 * 因此先按 UTF-8 严格解码，失败再按 ANSI 代码页解。</p>
 *
 * <p>读取一律走 {@link #read(Process)}：直接 {@code readAllBytes()} 会被继承管道写端的分离进程拖死。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public final class ConsoleOutput {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(ConsoleOutput.class);

    /**
     * UTF-8 解不动时使用的兜底字符集，即本机 ANSI 代码页
     */
    private static final Charset LEGACY = detectLegacy();

    /**
     * 前台命令的最长等待时间：再长就会被 MVC 的 30s 异步超时截成「系统忙」，不如自己先给出原因
     */
    private static final long DEFAULT_TIMEOUT_MS = 25_000L;

    /**
     * 子进程退出后留给管道搬运的收尾窗口，超过它就主动关句柄取已达到的输出
     */
    private static final long DRAIN_GRACE_MS = 300L;

    private ConsoleOutput() {
    }

    /**
     * 有界读取子进程输出并按 {@link #decode} 的口径解码。
     *
     * @param process 已启动的子进程
     * @return 命令输出文本，永不为 null
     */
    public static String read(Process process) {
        return read(process, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 有界读取子进程输出。
     *
     * <p>不能直接 {@code readAllBytes()}：{@code start} 出去的进程继承的是同一个管道写端，
     * 命令进程退出后 EOF 仍不到来，读取会一直等到那个进程结束。</p>
     *
     * @param process   已启动的子进程
     * @param timeoutMs 前台命令的最长等待时间
     * @return 命令输出文本，永不为 null
     */
    public static String read(Process process, long timeoutMs) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
// [P3C C1 豁免] 守护/后台线程：有独立生命周期，不适合交给通用执行器
        Thread pump = new Thread(() -> pump(process, buffer), "console-output-pump");
        pump.setDaemon(true);
        pump.start();
        boolean exited;
        try {
            exited = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("命令执行被中断", e);
        }
        if (!exited) {
            process.destroyForcibly();
            awaitQuiet(pump, DRAIN_GRACE_MS);
            throw new IllegalStateException("命令执行超时（" + timeoutMs / 1000 + "s），进程已强制结束");
        }
        awaitQuiet(pump, DRAIN_GRACE_MS);
        // destroy 会关掉读写句柄，把仍卡在 read 上的搬运线程叫醒，已到达的字节留在缓冲区里
        process.destroy();
        awaitQuiet(pump, DRAIN_GRACE_MS);
        return decode(buffer.toByteArray());
    }

    /**
     * 把子进程输出搬进缓冲区，直到 EOF 或句柄被关闭。
     *
     * @param process 子进程
     * @param buffer  输出缓冲
     */
    private static void pump(Process process, ByteArrayOutputStream buffer) {
        byte[] chunk = new byte[4096];
        try {
            InputStream in = process.getInputStream();
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } catch (Exception e) {
            // 收尾窗口到期由 destroy() 关句柄唤醒，属预期路径
            log.debug("[console] 输出搬运结束: {}", e.getMessage());
        }
    }

    /**
     * 等待搬运线程结束，超时或被中断都不向上抛。
     *
     * @param pump    搬运线程
     * @param millis  最长等待毫秒
     */
    private static void awaitQuiet(Thread pump, long millis) {
        try {
            pump.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解码命令输出。
     *
     * @param raw 原始字节，可为空
     * @return 文本，永不为 null
     */
    public static String decode(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(raw, LEGACY);
        }
    }

    /**
     * 取本机 ANSI 代码页。
     *
     * <p>{@code sun.jnu.encoding} 与 {@code native.encoding} 都直接反映 ACP，
     * 且不会被 JDK 18 起 {@code file.encoding} 默认 UTF-8 的规则影响。</p>
     *
     * @return 兜底字符集，取不到时 Windows 按 GBK、其余按 UTF-8
     */
    private static Charset detectLegacy() {
        for (String key : new String[]{"sun.jnu.encoding", "native.encoding"}) {
            Charset charset = toCharset(System.getProperty(key));
            if (charset != null) {
                return charset;
            }
        }
        return isWindows() ? Charset.forName("GBK") : StandardCharsets.UTF_8;
    }

    /**
     * 把系统属性里的字符集名转成字符集，名为空、不可用或就是 UTF-8 时返回 null。
     *
     * @param name 字符集名，可为空
     * @return 字符集，不可用时 null
     */
    private static Charset toCharset(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            Charset charset = Charset.forName(name.trim());
            return StandardCharsets.UTF_8.equals(charset) ? null : charset;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return 当前是否运行在 Windows 上
     */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}