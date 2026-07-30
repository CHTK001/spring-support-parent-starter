package com.chua.starter.panel.software.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SysProjectContainerSoftwareLogServiceImpl implements SysProjectContainerSoftwareLogService {

    private static final Logger log = LoggerFactory.getLogger(SysProjectContainerSoftwareLogServiceImpl.class);

    @Value("${plugin.software.log-dir:logs/software}")
    private String logBaseDir;

    private Path resolveLogFile(Integer bindingId, String action) {
        String safeAction = action != null ? action.replaceAll("[^a-zA-Z0-9_-]", "") : "UNKNOWN";
        return Paths.get(logBaseDir, String.valueOf(bindingId), safeAction + ".log");
    }

    private Path resolveLogFile(Integer bindingId, String action, String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return resolveLogFile(bindingId, action);
        }
        String safeAction = action != null ? action.replaceAll("[^a-zA-Z0-9_-]", "") : "UNKNOWN";
        String safeOperationId = operationId.replaceAll("[^a-zA-Z0-9_-]", "");
        return Paths.get(logBaseDir, String.valueOf(bindingId), safeAction, safeOperationId + ".log");
    }

    @Override
    public void appendLog(Integer bindingId, String action, String line) {
        appendLog(bindingId, action, null, line);
    }

    @Override
    public void appendLog(Integer bindingId, String action, String operationId, String line) {
        if (bindingId == null) return;
        try {
            Path logFile = resolveLogFile(bindingId, action, operationId);
            Files.createDirectories(logFile.getParent());
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            String entry = "[" + timestamp + "] " + line + System.lineSeparator();
            Files.writeString(logFile, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("写入操作日志失败: bindingId={}, action={}, operationId={}", bindingId, action, operationId, e);
        }
    }

    @Override
    public ReturnResult<List<String>> getLogs(Integer bindingId, String action) {
        return getLogs(bindingId, action, null);
    }

    @Override
    public ReturnResult<List<String>> getLogs(Integer bindingId, String action, String operationId) {
        if (bindingId == null) {
            return ReturnResult.ok(Collections.emptyList());
        }
        try {
            if (operationId != null && !operationId.isBlank()) {
                Path logFile = resolveLogFile(bindingId, action, operationId);
                if (Files.exists(logFile)) {
                    return ReturnResult.ok(Files.readAllLines(logFile, StandardCharsets.UTF_8));
                }
            }
            Path baseFile = resolveLogFile(bindingId, action);
            if (Files.exists(baseFile)) {
                return ReturnResult.ok(Files.readAllLines(baseFile, StandardCharsets.UTF_8));
            }
            // Fallback: scan action subdirectory for per-operationId log files
            String safeAction = action != null ? action.replaceAll("[^a-zA-Z0-9_-]", "") : "UNKNOWN";
            Path actionDir = Paths.get(logBaseDir, String.valueOf(bindingId), safeAction);
            if (Files.isDirectory(actionDir)) {
                List<String> allLines = new ArrayList<>();
                try (Stream<Path> files = Files.list(actionDir)) {
                    files.filter(p -> p.toString().endsWith(".log"))
                         .sorted()
                         .forEach(p -> {
                             try { allLines.addAll(Files.readAllLines(p, StandardCharsets.UTF_8)); }
                             catch (IOException e) { log.warn("读取日志文件失败: {}", p, e); }
                         });
                }
                return ReturnResult.ok(allLines);
            }
            return ReturnResult.ok(Collections.emptyList());
        } catch (IOException e) {
            log.warn("读取操作日志失败: bindingId={}, action={}, operationId={}", bindingId, action, operationId, e);
            return ReturnResult.ok(Collections.emptyList());
        }
    }
}
