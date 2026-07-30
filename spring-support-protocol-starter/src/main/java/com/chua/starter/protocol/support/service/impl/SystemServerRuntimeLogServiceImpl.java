package com.chua.starter.protocol.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.service.server.SystemServerRuntimeLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统服务器运行时日志（生命周期日志）实现：按 serverId/日期落文件。
 *
 * @author CH
 */
@Slf4j
@Service
public class SystemServerRuntimeLogServiceImpl implements SystemServerRuntimeLogService {

    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_BASE_DIR = "logs/system-server";

    @Override
    public void append(Integer serverId, String level, String event, String message) {
        if (serverId == null || serverId <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String safeLevel = StringUtils.isEmpty(level) ? "INFO" : level.toUpperCase();
        String safeEvent = StringUtils.isEmpty(event) ? "RUNTIME" : event.toUpperCase();
        String safeMessage = message == null ? "" : message.replace("\n", "\\n").replace("\r", "\\r");

        Path dateDir = Path.of(LOG_BASE_DIR, String.valueOf(serverId), now.toLocalDate().format(DATE_DIR_FORMATTER));
        try {
            Files.createDirectories(dateDir);
            Path logFile = dateDir.resolve("system.log");
            String line = String.join("|",
                    TIME_FORMATTER.format(now),
                    safeLevel,
                    safeEvent,
                    safeMessage
            ) + System.lineSeparator();
            Files.writeString(logFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ioe) {
            log.warn("写入系统运行日志失败: serverId={}, message={}", serverId, ioe.getMessage());
        }
    }

    @Override
    public ReturnResult<IPage<Map<String, Object>>> page(Page<?> page,
                                                        Integer serverId,
                                                        String level,
                                                        String event,
                                                        LocalDateTime startTime,
                                                        LocalDateTime endTime,
                                                        String keyword) {
        try {
            List<Map<String, Object>> all = read(serverId, level, event, startTime, endTime, keyword);
            long current = page.getCurrent() <= 0 ? 1 : page.getCurrent();
            long size = page.getSize() <= 0 ? 50 : page.getSize();
            int from = (int) ((current - 1) * size);
            int to = Math.min(all.size(), from + (int) size);
            List<Map<String, Object>> paged = from >= all.size() ? List.of() : all.subList(from, to);

            Page<Map<String, Object>> out = new Page<>(current, size);
            out.setTotal(all.size());
            out.setRecords(paged);
            return ReturnResult.success(out);
        } catch (Exception e) {
            log.error("分页查询系统运行日志失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> read(Integer serverId,
                                          String level,
                                          String event,
                                          LocalDateTime startTime,
                                          LocalDateTime endTime,
                                          String keyword) throws IOException {
        if (serverId == null || serverId <= 0) {
            return List.of();
        }
        Path serverDir = Path.of(LOG_BASE_DIR, String.valueOf(serverId));
        if (!Files.exists(serverDir)) {
            return List.of();
        }
        String lv = StringUtils.isEmpty(level) ? "" : level.toUpperCase();
        String ev = StringUtils.isEmpty(event) ? "" : event.toUpperCase();
        String kw = StringUtils.isEmpty(keyword) ? "" : keyword.toLowerCase();

        List<Map<String, Object>> result = new ArrayList<>();
        try (var dateDirs = Files.list(serverDir)) {
            for (Path dateDir : dateDirs.filter(Files::isDirectory).toList()) {
                Path logFile = dateDir.resolve("system.log");
                if (!Files.exists(logFile)) {
                    continue;
                }
                for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
                    Map<String, Object> item = parseLine(serverId, line);
                    if (item == null) {
                        continue;
                    }
                    if (!matches(item, lv, ev, startTime, endTime, kw)) {
                        continue;
                    }
                    result.add(item);
                }
            }
        }
        result.sort(Comparator
                .<Map<String, Object>, LocalDateTime>comparing(m -> (LocalDateTime) m.get("time"),
                        Comparator.nullsLast(LocalDateTime::compareTo))
                .reversed());
        return result;
    }

    private boolean matches(Map<String, Object> item,
                            String level,
                            String event,
                            LocalDateTime startTime,
                            LocalDateTime endTime,
                            String keyword) {
        if (!StringUtils.isEmpty(level)) {
            String lv = String.valueOf(item.getOrDefault("level", "")).toUpperCase();
            if (!lv.contains(level)) return false;
        }
        if (!StringUtils.isEmpty(event)) {
            String ev = String.valueOf(item.getOrDefault("event", "")).toUpperCase();
            if (!ev.contains(event)) return false;
        }
        LocalDateTime t = (LocalDateTime) item.get("time");
        if (startTime != null && (t == null || t.isBefore(startTime))) return false;
        if (endTime != null && (t == null || t.isAfter(endTime))) return false;
        if (!StringUtils.isEmpty(keyword)) {
            String msg = String.valueOf(item.getOrDefault("message", "")).toLowerCase();
            if (!msg.contains(keyword)) return false;
        }
        return true;
    }

    private Map<String, Object> parseLine(Integer serverId, String line) {
        if (line == null || line.isBlank()) return null;
        String[] parts = line.split("\\|", -1);
        if (parts.length < 4) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("serverId", serverId);
        m.put("time", parseTime(parts[0]));
        m.put("level", parts[1]);
        m.put("event", parts[2]);
        m.put("message", parts[3]);
        return m;
    }

    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), TIME_FORMATTER);
        } catch (Exception ignore) {
            // 尝试 date-only
            try {
                LocalDate d = LocalDate.parse(text.trim(), DATE_DIR_FORMATTER);
                return d.atStartOfDay();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}

