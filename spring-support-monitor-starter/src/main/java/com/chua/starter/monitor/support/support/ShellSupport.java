package com.chua.starter.monitor.support.support;

import com.chua.starter.monitor.support.enums.ShellDialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 远端命令拼装工具
 * <p>
 * 按目标通道真实运行的 shell 方言拼装工作目录，并植入三类探针：工作目录可达性探针、
 * 退出码探针、完成探针，使「目录不存在」「命令自身失败」「命令中断了 shell」不再被当成
 * 「执行成功且无输出」返回调用方。
 * </p>
 *
 * <p>方言实测差异（决定这里的拼装顺序，改动前请先在真机复测）：</p>
 * <ul>
 *   <li>cmd.exe：{@code cd /d 不存在的路径} 会把 ERRORLEVEL 置 1 并中断 {@code &&} 链，
 *       但 {@code &} 语句照旧执行，所以切目录必须用 {@code &&} 串联，否则命令会在默认目录里跑一遍；
 *       {@code if errorlevel N} 是执行期判定（{@code %ERRORLEVEL%} 是解析期展开，不可用），
 *       且 {@code if} 条件为假时会吞掉本行其后的全部 {@code &} 语句，所以失败探针必须放在行尾；</li>
 *   <li>/bin/sh：{@code cd} 失败会把 {@code $?} 置非零并中断 {@code &&} 链，但不会终止 {@code ;}
 *       后续语句；</li>
 *   <li>PowerShell：{@code $ErrorActionPreference='Stop'} 下 {@code Set-Location} 失败抛异常，
 *       原生程序退出码要读 {@code $LASTEXITCODE}；解析期错误（如未加引号的 {@code &}）不进 try/catch，
 *       整段脚本一句都不执行却仍以 0 退出，错误只以 {@code #&lt; CLIXML} 形态出现在输出流里，
 *       只能靠完成探针识破。</li>
 * </ul>
 *
 * @since 2026/9/21
 * @author CH
 */
public final class ShellSupport {

    /**
     * 失败探针标记：命令非零退出时由目标 shell 输出该行
     */
    public static final String FAIL_MARK = "__MS_CMD_FAIL__";

    /**
     * 失败探针的说明文字：cmd 的 {@code %ERRORLEVEL%} 是解析期展开（实测 {@code call echo %%ERRORLEVEL%%}
     * 只会得到 {@code %0%}），拿不到具体退出码，只能确定「非 0」这一事实。
     */
    private static final String FAIL_REASON = FAIL_MARK + ":命令退出码非 0";

    /**
     * 完成探针：整条命令活着跑到结尾才会输出该行。
     *
     * <p>行尾的失败探针依赖「命令之后还有语句可执行」，而 {@code exit 3} 这类命令会直接终结 shell，
     * PowerShell 的解析期错误更是整段脚本不执行，两者都留给调用方「无输出、无失败标记」的假成功。
     * 完成探针反过来要求「跑到结尾」的证据，缺失即失败。</p>
     */
    public static final String DONE_MARK = "__MS_CMD_DONE__";

    /**
     * 工作目录可达探针：切目录成功后输出该行
     */
    public static final String CWD_MARK = "__MS_CWD_OK__";

    /**
     * 目录不存在探针：Windows 两条通道列目录时用它区分「空目录」与「没有这个目录」。
     *
     * <p>不能靠退出码：{@code Get-ChildItem} 路径不存在是非终止错误，脚本仍以 0 退出、
     * 以空输出回报「列到了，但是空的」。</p>
     */
    public static final String NO_DIR_MARK = "__MS_NO_DIR__";

    /**
     * PowerShell 错误流的单条记录：{@code <S S="Error">…</S>}
     */
    private static final Pattern ERROR_RECORD = Pattern.compile("<S S=\"Error\">(.*?)</S>", Pattern.DOTALL);

    private ShellSupport() {
    }

    /**
     * 已下发的探针。
     *
     * @param line       下发给通道的命令行
     * @param cwdProbed  是否包含工作目录可达探针
     * @param doneProbed 是否要求完成探针（缺失即判失败）
     */
    public record Probe(String line, boolean cwdProbed, boolean doneProbed) {
    }

    /**
     * 探针还原结果。
     *
     * @param failed 工作目录不可达或命令非零退出
     * @param reason 失败原因，可为空
     * @param output 去掉探针行后的命令输出
     */
    public record ProbeResult(boolean failed, String reason, String output) {
    }

    /**
     * 拼装带工作目录与失败探针的命令行（不要求完成探针）。
     *
     * @param cwd     工作目录，可为空
     * @param cmd     命令
     * @param dialect 目标 shell 方言
     * @return 探针描述，永不为 null
     */
    public static Probe compose(String cwd, String cmd, ShellDialect dialect) {
        return compose(cwd, cmd, dialect, false);
    }

    /**
     * 拼装带工作目录与探针的命令行。
     *
     * @param cwd       工作目录，可为空
     * @param cmd       命令
     * @param dialect   目标 shell 方言
     * @param doneProbe 是否追加完成探针：命令把 shell 自己退出、或脚本压根没通过解析时，
     *                  行尾失败来不及执行，只有「跑到结尾」的证据能识破这种假成功
     * @return 探针描述，永不为 null
     */
    public static Probe compose(String cwd, String cmd, ShellDialect dialect, boolean doneProbe) {
        String dir = cwd == null ? "" : cwd.trim();
        return switch (dialect) {
            case CMD -> cmdDialect(dir, cmd, doneProbe);
            case SH -> shDialect(dir, cmd, doneProbe);
            case POWERSHELL -> powershell(dir, cmd, doneProbe);
        };
    }

    /**
     * 还原命令真实执行结果。
     *
     * @param raw   通道返回的原始输出，可为空
     * @param probe {@link #compose} 返回的探针描述
     * @return 探针还原结果，永不为 null
     */
    public static ProbeResult interpret(String raw, Probe probe) {
        Decoded decoded = decodeErrorStream(raw == null ? "" : raw);
        List<String> keep = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        boolean failSeen = false;
        boolean cwdSeen = false;
        boolean doneSeen = false;
        for (String line : decoded.output().split("\\r?\\n")) {
            if (line.contains(FAIL_MARK)) {
                failSeen = true;
                addReason(reasons, line.replace(FAIL_MARK, "").replace(":", ""));
                continue;
            }
            if (line.contains(CWD_MARK)) {
                cwdSeen = true;
                continue;
            }
            if (line.contains(DONE_MARK)) {
                doneSeen = true;
                continue;
            }
            // PowerShell 的 catch 分支把异常消息写在标记行的下一行
            if (failSeen) {
                addReason(reasons, line);
            } else {
                keep.add(line);
            }
        }
        boolean unfinished = probe.doneProbed() && !doneSeen && !failSeen;
        if (unfinished) {
            reasons.add("命令未跑到结尾：可能被命令自身 exit 中断，或未通过 shell 解析");
            // 解析期错误只以 CLIXML 形态存在，此时它是唯一的失败说明，必须让调用方读到
            for (String line : decoded.errors()) {
                addReason(reasons, line);
            }
        } else {
            keep.addAll(decoded.errors());
        }
        String output = String.join("\n", keep).trim();
        if (probe.cwdProbed() && !cwdSeen) {
            reasons.add(0, "工作目录不存在或不可进入");
        }
        boolean failed = failSeen || unfinished || (probe.cwdProbed() && !cwdSeen);
        String reason = String.join("; ", reasons).trim();
        return new ProbeResult(failed, reason.isEmpty() ? null : reason, output);
    }

    /**
     * 探针还原的输入拆分结果。
     *
     * @param output 去掉 CLIXML 错误流之后的命令标准输出
     * @param errors CLIXML 错误流还原出的可读文本行
     */
    private record Decoded(String output, List<String> errors) {

        /**
         * 规范构造器：对错误流行做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变。本 record 私有，
         * 两个构造点分别传 {@link List#of()} 与新聚的 ArrayList，
         * 元素取自正则捕获组，非 null。</p>
         *
         * @param errors CLIXML 错误流还原出的可读文本行，不能为 null
         */
        private Decoded {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors 不能为 null"));
        }
    }

    /**
     * 把通道原始输出/错误还原成人能读的文本。
     *
     * <p>用户可见的失败原因必须走本方法：PowerShell 把错误序列化成 {@code #< CLIXML <Objs…} 写进
     * 输出流，原样拼进异常消息，用户在页面上看到的就是一段 XML 头而不是一条错误说明。</p>
     *
     * @param text 通道原始输出或错误流，可为 null
     * @return 去掉 CLIXML 外壳、保留错误正文的文本，永不为 null
     */
    public static String readable(String text) {
        Decoded decoded = decodeErrorStream(text == null ? "" : text);
        List<String> parts = new ArrayList<>();
        addReason(parts, decoded.output());
        for (String error : decoded.errors()) {
            addReason(parts, error);
        }
        return String.join("\n", parts);
    }

    /**
     * 把 PowerShell 序列化进输出流的错误记录还原成普通文本。
     *
     * <p>解析期错误不进 try/catch，PowerShell 会改写整段脚本都不执行，并以
     * {@code #< CLIXML <Objs…><S S="Error">…</S></Objs>} 的形态把错误写进输出流；
     * 原样返回既读不懂，其中回显的源码行还可能撞上探针字样。</p>
     *
     * @param text 通道原始输出
     * @return 标准输出与还原后的错误行，永不为 null
     */
    private static Decoded decodeErrorStream(String text) {
        int at = text.indexOf("#< CLIXML");
        if (at < 0) {
            return new Decoded(text, List.of());
        }
        List<String> errors = new ArrayList<>();
        Matcher records = ERROR_RECORD.matcher(text.substring(at));
        while (records.find()) {
            errors.add(records.group(1)
                    .replace("_x000D__x000A_", "\n")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">"));
        }
        return new Decoded(text.substring(0, at), errors);
    }

    /**
     * cmd.exe 方言：cd → 目录探针 → 命令 → 行尾失败探针（需要时配成 if/else 带完成探针）。
     *
     * @param dir       工作目录，可为空
     * @param cmd       命令
     * @param doneProbe 是否追加完成探针
     * @return 探针描述
     */
    private static Probe cmdDialect(String dir, String cmd, boolean doneProbe) {
        // 完成探针必须与失败探针合成一个 if/else 块：实测条件为假的 if 会吞掉本行其后的 & 语句，
        // 裸写 "& echo 完成标记" 会让成功路径也读不到它。
        String tail = doneProbe
                ? "(if errorlevel 1 (echo " + FAIL_REASON + ") else (echo " + DONE_MARK + "))"
                : "if errorlevel 1 echo " + FAIL_REASON;
        String normalized = dir.replace("\"", "").replace("/", "\\");
        // cd /d 只对盘符路径可靠；"/"、"\\" 这类形式会让引号配对失效，按不切目录处理
        if (normalized.length() < 3 || normalized.charAt(1) != ':') {
            return new Probe(cmd + " & " + tail, false, doneProbe);
        }
        // 必须用 && 串联：实测 cd /d 不存在的路径会置 ERRORLEVEL=1，& 却照样执行后面的语句，
        // 旧实现会把「删除/重命名」在默认目录下执行一遍，只在最后报一个失败。
        return new Probe("cd /d \"" + normalized + "\" && echo " + CWD_MARK + " && " + cmd
                + " & " + tail, true, doneProbe);
    }

    /**
     * /bin/sh 方言：cd 失败会中断 &amp;&amp; 链并把状态交给探针。
     *
     * @param dir       工作目录，可为空
     * @param cmd       命令
     * @param doneProbe 是否追加完成探针
     * @return 探针描述
     */
    private static Probe shDialect(String dir, String cmd, boolean doneProbe) {
        String tail = "; if [ $? -ne 0 ]; then echo " + FAIL_REASON + "; fi"
                + (doneProbe ? "; echo " + DONE_MARK : "");
        if (dir.isEmpty()) {
            return new Probe(cmd + tail, false, doneProbe);
        }
        String body = "cd '" + dir.replace("'", "") + "' && { echo " + CWD_MARK + "; " + cmd + "; }";
        return new Probe(body + tail, true, doneProbe);
    }

    /**
     * PowerShell 方言：Set-Location 切目录，try/catch 与 LASTEXITCODE 双通道捕获失败。
     *
     * @param dir       工作目录，可为空
     * @param cmd       命令
     * @param doneProbe 是否追加完成探针
     * @return 探针描述
     */
    private static Probe powershell(String dir, String cmd, boolean doneProbe) {
        StringBuilder sb = new StringBuilder();
        sb.append("$ErrorActionPreference='Stop'; $global:LASTEXITCODE=0; try { ");
        boolean probed = !dir.isEmpty();
        if (probed) {
            sb.append("Set-Location -LiteralPath '").append(dir.replace("'", "''")).append("'; ");
        }
        sb.append("Write-Output '").append(CWD_MARK).append("'; ").append(cmd);
        sb.append("; if ($LASTEXITCODE -ne 0) { Write-Output ('").append(FAIL_MARK).append(":退出码 '")
                .append(" + $LASTEXITCODE) } ");
        sb.append("} catch { Write-Output ('").append(FAIL_MARK).append(":'); ")
                .append("Write-Output $_.Exception.Message }");
        if (doneProbe) {
            // 解析期错误会让整段脚本一句都不执行，只有跑完 catch 才可能打出这一行
            sb.append("; Write-Output '").append(DONE_MARK).append("'");
        }
        return new Probe(sb.toString(), probed, doneProbe);
    }

    /**
     * 收集非空的失败原因行。
     *
     * @param reasons 原因收集列表
     * @param line    候选行
     */
    private static void addReason(List<String> reasons, String line) {
        String trimmed = line == null ? "" : line.trim();
        if (!trimmed.isEmpty()) {
            reasons.add(trimmed);
        }
    }
}
