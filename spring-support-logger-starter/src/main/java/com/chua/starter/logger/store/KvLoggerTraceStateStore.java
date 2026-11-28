package com.chua.starter.logger.store;

import com.chua.common.support.lang.datasource.kv.KvEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Logger 使用的持久化 KV 引擎保存 trace 状态。
 *
 * <p>默认是 JDBC KV，因此状态最终落在数据库磁盘；配置 redis-bean 时复用 Redis
 * 持久化实现。实现只在单次读写期间保留一个状态对象，不按客户端数量建立内存索引。</p>
 */
@Slf4j
public class KvLoggerTraceStateStore implements LoggerTraceStateStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TRACE_PREFIX = "logger-trace:";
    private static final String RATE_PREFIX = "logger-trace-rate:";
    private static final long TRACE_TTL_MS = 60 * 60_000L;
    private static final long RATE_TTL_MS = 31 * 60_000L;
    private static final int SCAN_BATCH = 500;

    private final KvEngine kv;

    public KvLoggerTraceStateStore(KvEngine kv) {
        if (kv == null) {
            throw new IllegalArgumentException("kv 不能为空");
        }
        this.kv = kv;
    }

    @Override
    public synchronized TraceState getTrace(String traceId, long now) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        String key = TRACE_PREFIX + traceId;
        TraceState state = read(key, TraceState.class);
        if (state == null) {
            return null;
        }
        if (now - state.createdAt() >= TRACE_TTL_MS) {
            kv.delete(key);
            return null;
        }
        return state;
    }

    @Override
    public synchronized boolean createTrace(TraceState state) {
        if (state == null || state.traceId() == null || state.traceId().isBlank()) {
            return false;
        }
        String key = TRACE_PREFIX + state.traceId();
        if (kv.get(key) != null) {
            return false;
        }
        write(key, state, TRACE_TTL_MS);
        return true;
    }

    @Override
    public synchronized boolean updateTracePhases(String traceId, int expectedPhases, int phases) {
        TraceState current = getTrace(traceId, System.currentTimeMillis());
        if (current == null || current.phases() != expectedPhases) {
            return false;
        }
        write(TRACE_PREFIX + traceId,
                new TraceState(current.traceId(), current.userId(), current.app(), current.createdAt(), phases),
                Math.max(1L, TRACE_TTL_MS - (System.currentTimeMillis() - current.createdAt())));
        return true;
    }

    @Override
    public synchronized RateState getRate(String userId, String app, long now) {
        String key = rateKey(userId, app);
        RateState state = read(key, RateState.class);
        if (state == null) {
            return null;
        }
        if (now - state.updatedAt() >= RATE_TTL_MS) {
            kv.delete(key);
            return null;
        }
        return state;
    }

    @Override
    public synchronized void saveRate(RateState state) {
        if (state == null) {
            return;
        }
        write(rateKey(state.userId(), state.app()), state, RATE_TTL_MS);
    }

    @Override
    public synchronized List<RateState> findBlocked(long now, int limit) {
        int safeLimit = Math.max(1, limit);
        List<RateState> result = new ArrayList<>(Math.min(safeLimit, 64));
        scanRates(now, (key, state) -> {
            if (state.blockedUntil() > now && result.size() < safeLimit) {
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
        scanRates(now, (key, state) -> {
            boolean matches = byApp ? value.equals(state.app()) : value.equals(state.userId());
            if (matches && state.blockedUntil() > now) {
                kv.delete(key);
                cleared[0]++;
            }
            return true;
        });
        return cleared[0];
    }

    private void scanRates(long now, RateVisitor visitor) {
        String afterKey = "";
        while (true) {
            Map<String, String> batch = kv.findAllByPrefix(RATE_PREFIX, afterKey, SCAN_BATCH);
            if (batch.isEmpty()) {
                return;
            }
            for (Map.Entry<String, String> entry : batch.entrySet()) {
                afterKey = entry.getKey();
                RateState state = read(entry.getKey(), entry.getValue(), RateState.class);
                if (state == null) {
                    continue;
                }
                if (now - state.updatedAt() >= RATE_TTL_MS) {
                    kv.delete(entry.getKey());
                    continue;
                }
                if (!visitor.visit(entry.getKey(), state)) {
                    return;
                }
            }
        }
    }

    private String rateKey(String userId, String app) {
        return RATE_PREFIX + digest(String.valueOf(userId) + '\u0000' + app);
    }

    private <T> T read(String key, Class<T> type) {
        return read(key, kv.get(key), type);
    }

    private <T> T read(String key, String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.warn("[logger] trace 状态损坏，已跳过: key={}, reason={}", key, e.getMessage());
            return null;
        }
    }

    private void write(String key, Object state, long ttlMs) {
        try {
            String json = MAPPER.writeValueAsString(state);
            if (kv.supportsTtl()) {
                kv.put(key, json, Duration.ofMillis(Math.max(1L, ttlMs)));
            } else {
                kv.put(key, json);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Logger trace 状态落盘失败: " + key, e);
        }
    }

    static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算 trace 状态键失败", e);
        }
    }

    @FunctionalInterface
    private interface RateVisitor {
        boolean visit(String key, RateState state);
    }
}
