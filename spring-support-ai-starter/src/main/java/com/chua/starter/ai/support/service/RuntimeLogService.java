package com.chua.starter.ai.support.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运行日志读取服务。
 *
 * <p>职责：解析标准输出和标准错误日志文件路径，以只读方式从文件尾部提取有限行数日志，清理 ANSI 控制序列并识别日志级别后返回给管理端。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class RuntimeLogService {

    /** 单次返回的最大字节数，防止日志过大撑爆响应 */
    private static final int MAX_TAIL_BYTES = 512 * 1024;
    /** 单行最大长度截断 */
    private static final int MAX_LINE_LEN = 4000;

    private static final Pattern LEVEL_PATTERN =
            Pattern.compile("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");

    /** ANSI 颜色 / 控制转义序列（logback 彩色输出），展示前需剥离 */
    private static final Pattern ANSI_PATTERN =
            Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");

    private final Environment environment;

    @Value("${ai.runtime-log.out-path:}")
    private String configuredOutPath;
    @Value("${ai.runtime-log.err-path:}")
    private String configuredErrPath;

    public RuntimeLogService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void init() {
        File out = resolveFile("out");
        log.info("[RuntimeLog] 运行日志(out)解析为: {} (exists={})",
                out == null ? null : out.getAbsolutePath(), out != null && out.exists());
    }

    /** 一条日志行 */
    public record LogLine(String raw, String level) {
    }

    /**
     * tail 结果。
     *
     * @param path 日志文件路径，文件无法定位时为 {@code null}
     * @param exists 文件是否存在
     * @param lastModified 最后修改时间戳
     * @param truncated 是否因行数上限被截断
     * @param lines 日志行列表
     */
    public record TailResult(String path, boolean exists, long lastModified,
                             boolean truncated, List<LogLine> lines) {

        /**
         * 规范构造器：对日志行列表做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变。</p>
         *
         * @param lines 日志行列表
         */
        public TailResult {
            lines = List.copyOf(Objects.requireNonNull(lines, "lines 不能为 null"));
        }
    }

    /**
     * 读取指定来源日志的尾部。
     *
     * @param source out=标准输出（app-out），err=标准错误（app-err）
     * @param lines  期望返回的最大行数（最终还会受字节上限约束）
     * @return tail 结果，文件不存在时返回空结果而非异常
     */
    public TailResult tail(String source, int lines) {
        int wantLines = Math.min(Math.max(lines, 20), 2000);
        File file = resolveFile(source);
        if (file == null || !file.exists() || !file.isFile()) {
            return new TailResult(file == null ? null : file.getAbsolutePath(),
                    false, 0, false, List.of());
        }
        List<String> rawLines = readTailLines(file, wantLines);
        boolean truncated = rawLines.size() >= wantLines;
        List<LogLine> out = new ArrayList<>(rawLines.size());
        for (String raw : rawLines) {
            String clean = stripAnsi(raw);
            out.add(new LogLine(truncate(clean), detectLevel(clean)));
        }
        return new TailResult(file.getAbsolutePath(), true, file.lastModified(), truncated, out);
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_LINE_LEN ? s.substring(0, MAX_LINE_LEN) + "…" : s;
    }

    private String stripAnsi(String s) {
        if (s == null || s.indexOf('\u001B') < 0) {
            return s;
        }
        return ANSI_PATTERN.matcher(s).replaceAll("");
    }

    private String detectLevel(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = LEVEL_PATTERN.matcher(raw);
        if (!m.find()) {
            return null;
        }
        String level = m.group(1);
        return "WARNING".equals(level) ? "WARN" : level;
    }

    private File resolveFile(String source) {
        boolean err = "err".equalsIgnoreCase(source);
        // 1. 显式配置
        String configured = err ? configuredErrPath : configuredOutPath;
        if (configured != null && !configured.isBlank()) {
            return new File(configured.trim());
        }
        // 2. Spring logging.file.name（err 没有独立文件，复用同一文件）
        String loggingFile = environment.getProperty("logging.file.name");
        if (loggingFile != null && !loggingFile.isBlank()) {
            return new File(loggingFile.trim());
        }
        // 3. user.dir 下的重定向文件
        String fileName = err ? "app-err.log" : "app-out.log";
        return new File(System.getProperty("user.dir"), fileName);
    }

    /**
     * 从文件尾部向前读取，按行返回（保持正序）。
     * 使用 RandomAccessFile seek 到尾部后逐字节回退，避免把整个大文件读入内存。
     */
    private List<String> readTailLines(File file, int wantLines) {
        List<String> reversed = new ArrayList<>(wantLines);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length <= 0) {
                return List.of();
            }
            long pos = length;
            int bytesBudget = MAX_TAIL_BYTES;
            java.io.ByteArrayOutputStream current = new java.io.ByteArrayOutputStream();
            boolean skippedFirstNl = false;

            while (pos > 0 && reversed.size() < wantLines && bytesBudget > 0) {
                pos--;
                raf.seek(pos);
                int b = raf.read();
                bytesBudget--;
                if (b == '\n') {
                    // 行结束（向前方向）；忽略文件最末尾的换行
                    if (!skippedFirstNl) {
                        skippedFirstNl = true;
                        current.reset();
                        continue;
                    }
                    addLine(reversed, current);
                    current = new java.io.ByteArrayOutputStream();
                } else if (b == '\r') {
                    // 跳过 CR（CRLF）
                    continue;
                } else {
                    current.write(b);
                }
            }
            // 最后一行（文件最前面的残余）
            if (current.size() > 0 && reversed.size() < wantLines) {
                addLine(reversed, current);
            }
        } catch (IOException e) {
            log.warn("[RuntimeLog] 读取日志失败 {}: {}", file.getAbsolutePath(), e.getMessage());
        }
        // 反序 → 正序
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private void addLine(List<String> reversed, java.io.ByteArrayOutputStream current) {
        byte[] bytes = current.toByteArray();
        // current 是逆序累积的，需要反转
        for (int i = 0, j = bytes.length - 1; i < j; i++, j--) {
            byte t = bytes[i];
            bytes[i] = bytes[j];
            bytes[j] = t;
        }
        reversed.add(new String(bytes, StandardCharsets.UTF_8));
    }
}
