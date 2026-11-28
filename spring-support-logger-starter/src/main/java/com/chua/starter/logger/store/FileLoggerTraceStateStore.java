package com.chua.starter.logger.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 无 DataSource/Redis 时的本地磁盘状态存储。
 *
 * <p>每个 trace 和频控身份各占一个小 JSON 文件，不把全部客户端读入内存；临时文件
 * 原子替换，避免进程中断留下半个 JSON。多实例部署应优先使用 JDBC 或 Redis，文件
 * 兜底只适用于单实例。</p>
 */
@Slf4j
public class FileLoggerTraceStateStore implements LoggerTraceStateStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TRACE_TTL_MS = 60 * 60_000L;
    private static final long RATE_TTL_MS = 31 * 60_000L;

    private final Path traceDir;
    private final Path rateDir;

    public FileLoggerTraceStateStore(String rootPath) {
        Path root = Path.of(rootPath == null || rootPath.isBlank()
                ? "./data/logger/trace-state" : rootPath).toAbsolutePath().normalize();
        this.traceDir = root.resolve("traces");
        this.rateDir = root.resolve("rates");
        try {
            Files.createDirectories(traceDir);
            Files.createDirectories(rateDir);
        } catch (IOException e) {
            throw new IllegalStateException("Logger trace 磁盘目录创建失败: " + root, e);
        }
    }

    @Override
    public synchronized TraceState getTrace(String traceId, long now) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        Path file = traceDir.resolve(KvLoggerTraceStateStore.digest(traceId) + ".json");
        TraceState state = read(file, TraceState.class);
        if (state == null) {
            return null;
        }
        if (now - state.createdAt() >= TRACE_TTL_MS) {
            delete(file);
            return null;
        }
        return state;
    }

    @Override
    public synchronized boolean createTrace(TraceState state) {
        if (state == null || state.traceId() == null || state.traceId().isBlank()) {
            return false;
        }
        cleanupTraces(System.currentTimeMillis(), 32);
        Path file = traceDir.resolve(KvLoggerTraceStateStore.digest(state.traceId()) + ".json");
        if (Files.exists(file)) {
            return false;
        }
        try {
            Files.writeString(file, MAPPER.writeValueAsString(state), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return true;
        } catch (java.nio.file.FileAlreadyExistsException e) {
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Logger trace 磁盘登记失败", e);
        }
    }

    @Override
    public synchronized boolean updateTracePhases(String traceId, int expectedPhases, int phases) {
        Path file = traceDir.resolve(KvLoggerTraceStateStore.digest(traceId) + ".json");
        TraceState current = getTrace(traceId, System.currentTimeMillis());
        if (current == null || current.phases() != expectedPhases) {
            return false;
        }
        write(file, new TraceState(current.traceId(), current.userId(), current.app(),
                current.createdAt(), phases));
        return true;
    }

    @Override
    public synchronized RateState getRate(String userId, String app, long now) {
        Path file = rateDir.resolve(KvLoggerTraceStateStore.digest(String.valueOf(userId)
                + '\u0000' + app) + ".json");
        RateState state = read(file, RateState.class);
        if (state == null) {
            return null;
        }
        if (now - state.updatedAt() >= RATE_TTL_MS) {
            delete(file);
            return null;
        }
        return state;
    }

    @Override
    public synchronized void saveRate(RateState state) {
        if (state == null) {
            return;
        }
        cleanupRates(state.updatedAt(), 32);
        Path file = rateDir.resolve(KvLoggerTraceStateStore.digest(String.valueOf(state.userId())
                + '\u0000' + state.app()) + ".json");
        write(file, state);
    }

    @Override
    public synchronized List<RateState> findBlocked(long now, int limit) {
        int safeLimit = Math.max(1, limit);
        List<RateState> result = new ArrayList<>(Math.min(safeLimit, 64));
        scanRates(now, (file, state) -> {
            if (state.blockedUntil() > now) {
                result.add(state);
            }
            return result.size() < safeLimit;
        });
        return result;
    }

    @Override
    public synchronized int clearBlocked(String scope, String value, long now) {
        boolean byApp = "app".equals(scope);
        boolean byUser = "user".equals(scope);
        if ((!byApp && !byUser) || value == null || value.isBlank()) {
            return 0;
        }
        int[] cleared = {0};
        scanRates(now, (file, state) -> {
            boolean matches = byApp ? value.equals(state.app()) : value.equals(state.userId());
            if (matches && state.blockedUntil() > now) {
                delete(file);
                cleared[0]++;
            }
            return true;
        });
        return cleared[0];
    }

    private void scanRates(long now, RateVisitor visitor) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(rateDir, "*.json")) {
            for (Path file : files) {
                RateState state = read(file, RateState.class);
                if (state == null) {
                    continue;
                }
                if (now - state.updatedAt() >= RATE_TTL_MS) {
                    delete(file);
                    continue;
                }
                if (!visitor.visit(file, state)) {
                    return;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Logger trace 频控状态扫描失败", e);
        }
    }

    private void cleanupTraces(long now, int maxFiles) {
        int cleaned = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(traceDir, "*.json")) {
            for (Path file : files) {
                if (cleaned >= maxFiles) {
                    return;
                }
                TraceState trace = read(file, TraceState.class);
                if (trace != null && now - trace.createdAt() >= TRACE_TTL_MS) {
                    delete(file);
                    cleaned++;
                }
            }
        } catch (IOException e) {
            log.warn("[logger] trace 磁盘状态清理失败: {}", e.getMessage());
        }
    }

    private void cleanupRates(long now, int maxFiles) {
        int cleaned = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(rateDir, "*.json")) {
            for (Path file : files) {
                if (cleaned >= maxFiles) {
                    return;
                }
                RateState rate = read(file, RateState.class);
                if (rate != null && now - rate.updatedAt() >= RATE_TTL_MS) {
                    delete(file);
                    cleaned++;
                }
            }
        } catch (IOException e) {
            log.warn("[logger] trace 频控磁盘状态清理失败: {}", e.getMessage());
        }
    }

    private <T> T read(Path file, Class<T> type) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            log.warn("[logger] trace 磁盘状态损坏，已跳过: file={}, reason={}", file, e.getMessage());
            return null;
        }
    }

    private void write(Path file, Object state) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temp, MAPPER.writeValueAsString(state), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            delete(temp);
            throw new IllegalStateException("Logger trace 磁盘状态写入失败: " + file, e);
        }
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("[logger] trace 状态文件删除失败: {}", file, e);
        }
    }

    @FunctionalInterface
    private interface RateVisitor {
        boolean visit(Path file, RateState state);
    }
}
