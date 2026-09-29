package com.chua.starter.monitor.support.nginx.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.nginx.spi.NginxExecutor;
import com.chua.starter.monitor.support.spi.Platforms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 本机执行通道：命令直接在<strong>控制台所在机器</strong>上跑，不经 SSH。
 *
 * <p>存在理由：Windows 上跑 nginx 不必、也常常不能配 SSH。控制台自己就是 Windows
 * （本机实测 {@code C:\nginx\nginx.exe} 1.28.0），直接 {@code cmd /c} 起进程即可。</p>
 *
 * <p>本通道<strong>不</strong>判断平台：它只按控制台自身的 OS 选择解释器
 * （Windows 用 {@code cmd /c}，其余用 {@code sh -c}）。目标平台的命令文本由
 * {@code NginxCommandProvider} 决定，两者在路由器里配对——若把命令提供者与执行器
 * 配错（例如 Windows 命令给了 {@code sh}），会在执行时报语法错，所以路由器必须做
 * 一致性校验。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "nginx-local", order = 300)
public class LocalNginxExecutor implements NginxExecutor {

    /**
     * 控制台所在平台，构造后不变。
     */
    private static final String LOCAL_PLATFORM = Platforms.normalize(System.getProperty("os.name"));

    @Override
    public boolean supports(Target target) {
        return target != null && target.local();
    }

    @Override
    public String name() {
        return "nginx-local";
    }

    @Override
    public ExecResult exec(Target target, String command, long seconds) {
        Path script = null;
        ProcessBuilder pb;
        try {
            if (Platforms.WINDOWS.equals(LOCAL_PLATFORM)) {
                // 必须经临时 .cmd 文件，不能直接 [cmd, /c, command]。
                // 实测：Java 的 ProcessBuilder 在 Windows 上会把含引号的参数转义成 \"，
                // 而 cmd 不认 \"，于是「exit=0 但 stdout 为空」——静默失败，最难查。
                // 直接 PowerShell argv 或写进 .cmd 文件都能绕开这层转义。
                // chcp 65001 让子进程按 UTF-8 输出，与下方按 UTF-8 解码对齐，
                // 否则中文路径/中文日志会变成乱码。
                script = Files.createTempFile("nginx-console-", ".cmd");
                Files.write(script,
                        ("@echo off\r\nchcp 65001 >nul\r\n" + command + "\r\n").getBytes(StandardCharsets.UTF_8));
                pb = new ProcessBuilder("cmd", "/c", script.toString());
            } else {
                // POSIX 的 exec 直接传 argv，不存在二次转义问题，sh -c 即可
                pb = new ProcessBuilder("sh", "-c", command);
            }
            pb.redirectErrorStream(false);
            Process process = pb.start();
            // 必须先读完两个流再 waitFor：命令输出超过管道缓冲区时，
            // 子进程会阻塞在写端、永远不退出，waitFor 就会一直等到超时。
            Streams out = readAsync(process.getInputStream());
            Streams err = readAsync(process.getErrorStream());
            boolean finished = process.waitFor(seconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("本机执行超时（" + seconds + "s）命令[" + command + "]");
            }
            out.join();
            err.join();
            return new ExecResult(process.exitValue(), out.text(), err.text());
        } catch (IOException e) {
            throw new IllegalStateException("本机执行失败 命令[" + command + "]: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("本机执行被中断 命令[" + command + "]", e);
        } finally {
            if (script != null) {
                try {
                    Files.deleteIfExists(script);
                } catch (IOException ignored) {
                    // 临时文件删不掉不影响结果，交给系统清理
                }
            }
        }
    }

    /**
     * 控制台自身平台。
     *
     * @return WINDOWS / LINUX / MACOS
     */
    public static String localPlatform() {
        return LOCAL_PLATFORM;
    }

    /**
     * 异步读流：返回句柄，交由调用方在进程结束后 join。
     *
     * @param in 输入流
     * @return 读取句柄
     */
    private static Streams readAsync(InputStream in) {
        Streams holder = new Streams();
        Thread t = new Thread(() -> {
            try (InputStream stream = in) {
                holder.text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                holder.text = "";
            }
        }, "nginx-local-reader");
        t.setDaemon(true);
        holder.thread = t;
        t.start();
        return holder;
    }

    /**
     * 流读取结果。
     */
    private static final class Streams {

        /**
         * 读取线程
         */
        private Thread thread;

        /**
         * 读到的文本
         */
        private volatile String text = "";

        /**
         * 等待读取完成。
         */
        private void join() {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * 读取到的文本。
         *
         * @return 文本
         */
        private String text() {
            return text == null ? "" : text;
        }
    }
}
