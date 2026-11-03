package com.chua.starter.server.support.probe;

import com.chua.starter.server.support.provider.project.PosixProjectPlatformProvider;
import com.chua.starter.server.support.provider.project.WindowsProjectPlatformProvider;
import com.chua.starter.server.support.spi.ProjectTypeProviderRegistry;
import com.chua.starter.server.support.util.ProjectScripts;

/**
 * 打印平台会下发的四段脚本与健康检查命令，供目标机上的自检脚本原样执行。
 *
 * <p>用途：当平台到目标机的 exec 通道不可用（实测 Windows 上曾整机返回
 * {@code Access is denied.}）时，无法通过平台做真机验收。本生成器把
 * 「平台将会下发什么」原样吐出来，让目标机控制台能脱离平台复现同一批命令，
 * 从而仍然能对脚本本身做真机验证。</p>
 *
 * <p>输出用 {@code ===XXX===} 分节，便于 PowerShell 侧解析。</p>
 *
 * <p>用法：{@code java ... ReleaseScriptPrinter <linux|windows> <type> <entry> <path> <log> <appName> [port]}</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ReleaseScriptPrinter {

    /**
     * 项目类型路由器：脚本按项目类型 SPI 生成，与生产链路同源。
     */
    private static final ProjectTypeProviderRegistry TYPES = new ProjectTypeProviderRegistry();

    private ReleaseScriptPrinter() {
    }

    /**
     * 入口。
     *
     * @param args 见类注释
     */
    public static void main(String[] args) {
        if (args.length < 6) {
            System.err.println("usage: ReleaseScriptPrinter <linux|windows> <type> <entry> <path> <log> <appName> [port]"); // [P3C E1 豁免]
            System.exit(2);
        }
        boolean windows = "linux".equalsIgnoreCase(args[0]) ? false : "windows".equalsIgnoreCase(args[0]);
        String type = args[1];
        String entry = args[2];
        String path = args[3];
        String log = args[4];
        String appName = args[5];
        int port = args.length > 6 ? Integer.parseInt(args[6]) : 0;

        // __JVMARGS__ / __ARGS__ 在真实发布链路由 ReleaseServiceImpl 替换，生成器这里也要处理，
        // 否则吐出来的脚本会带着字面占位符。端口给定时把 __ARGS__ 填成 --server.port。
        String argLiteral = port > 0 ? "--server.port=" + port : "";
        ProjectScripts.Scripts raw = TYPES.resolveOrGeneric(type).scripts(windows, entry);
        section("START", subst(ProjectScripts.apply(raw.start(), entry, path, log, appName), argLiteral));
        section("STOP", ProjectScripts.apply(raw.stop(), entry, path, log, appName));
        section("RESTART", subst(ProjectScripts.apply(raw.restart(), entry, path, log, appName), argLiteral));
        section("STATUS", ProjectScripts.apply(raw.status(), entry, path, log, appName));

        String guarded = windows
                ? new WindowsProjectPlatformProvider().guardedRestart(path, raw.restart(), raw.stop(),
                raw.start(), raw.status(), true, port)
                : new PosixProjectPlatformProvider().guardedRestart(path, raw.restart(), raw.stop(),
                raw.start(), raw.status(), true, port);
        section("GUARDED", subst(guarded, argLiteral));

        // 单独抽出端口探活函数：自检脚本要能脱离整段健康检查单独调用它
        if (port > 0) {
            section("LPORT", extractListeningPort(guarded));
        }
    }

    /**
     * 替换 JVM 参数与应用参数占位符。
     *
     * @param text       脚本文本
     * @param argLiteral 应用参数字面量
     * @return 替换后的文本
     */
    private static String subst(String text, String argLiteral) {
        if (text == null) {
            return "";
        }
        return text.replace("__JVMARGS__", "").replace("__ARGS__", argLiteral);
    }

    /**
     * 打印一个分节。
     *
     * @param name    分节名
     * @param content 内容
     */
    private static void section(String name, String content) {
        System.out.println("===" + name + "==="); // [P3C E1 豁免]
        System.out.println(content == null ? "" : content); // [P3C E1 豁免]
    }

    /**
     * 从健康检查命令中抽出端口探活函数定义。
     *
     * <p>只取 {@code listening_port() {} 到首个顶格 {@code }} 之间的内容；
     * 抽不到时返回空串，由调用方按「探活函数缺失」处理。</p>
     *
     * @param guarded 健康检查命令
     * @return 函数定义文本；不存在时为空串
     */
    private static String extractListeningPort(String guarded) {
        if (guarded == null) {
            return "";
        }
        // 两种形态都要认：Posix 是 `listening_port() {`，Windows 是
        // `function listening_port([int]$want) {`（带 function 关键字与带参签名）。
        // 这里只按函数名定位，再往前取到行首，从而把 `function ` 前缀一并带上。
        int at = guarded.indexOf("listening_port(");
        if (at < 0) {
            return "";
        }
        int lineStart = guarded.lastIndexOf('\n', at);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        int end = guarded.indexOf("\n}\n", at);
        return end < 0 ? guarded.substring(lineStart) : guarded.substring(lineStart, end + 3);
    }
}
