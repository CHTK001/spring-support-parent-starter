package com.chua.starter.logger.server;

import com.chua.starter.logger.LoggerAutoConfiguration;
import com.chua.starter.logger.event.LoggerEvent;
import com.chua.starter.logger.properties.LoggerProperties;
import com.chua.starter.logger.store.LoggerEventBus;
import com.chua.starter.logger.store.LoggerEventStore;
import com.chua.starter.logger.store.LoggerTraceStateStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理浏览器 SDK 的请求级 trace 登记、双阶段上报与频控。
 *
 * <p>登记、阶段位和频控状态全部交给持久化状态存储，服务端堆内不按客户端数量保留
 * Map；请求身份来自 OAuth 认证过滤器写入的 userId 属性，不使用 X-Trace-Id 作为
 * 身份或鉴权材料。</p>
 */
public class LoggerTraceRegistry {

    private static final long WINDOW_MS = 60_000L;
    private static final long BLOCK_MS = 30 * 60_000L;
    private static final long TRACE_TTL_MS = 60 * 60_000L;

    private final LoggerEventStore store;
    private final LoggerEventBus bus;
    private final LoggerTraceStateStore stateStore;
    private final LoggerProperties properties;

    public LoggerTraceRegistry(LoggerEventStore store, LoggerEventBus bus,
                               LoggerTraceStateStore stateStore, LoggerProperties properties) {
        this.store = store;
        this.bus = bus;
        this.stateStore = stateStore;
        this.properties = properties;
    }

    public synchronized Map<String, Object> register(String userId, String app) {
        long now = System.currentTimeMillis();
        LoggerTraceStateStore.RateState oldRate = stateStore.getRate(userId, app, now);
        if (oldRate != null && oldRate.blockedUntil() > now) {
            throw blocked(oldRate.blockedUntil());
        }
        int limit = Math.max(1, properties.getServer().getTraceRequestsPerMinute());
        long windowStart = now - (now % WINDOW_MS);
        int count = oldRate != null && oldRate.windowStart() == windowStart ? oldRate.count() : 0;
        if (count >= limit) {
            long blockedUntil = now + BLOCK_MS;
            stateStore.saveRate(new LoggerTraceStateStore.RateState(
                    userId, app, windowStart, count, blockedUntil, now));
            throw blocked(blockedUntil);
        }
        stateStore.saveRate(new LoggerTraceStateStore.RateState(
                userId, app, windowStart, count + 1, 0L, now));
        String traceId = UUID.randomUUID().toString().replace("-", "");
        if (!stateStore.createTrace(new LoggerTraceStateStore.TraceState(traceId, userId, app, now, 0))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "traceId 登记失败");
        }
        return Map.of("traceId", traceId, "expiresInMs", TRACE_TTL_MS);
    }

    public synchronized void report(String userId, String app, String traceId, String phase,
                                    String method, String path, Integer status, Long durationMs) {
        long now = System.currentTimeMillis();
        if (traceId == null || traceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "traceId 不能为空");
        }
        LoggerTraceStateStore.TraceState trace = stateStore.getTrace(traceId, now);
        if (trace == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "traceId 未登记或已过期");
        }
        if (!trace.userId().equals(userId) || !trace.app().equals(app)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "traceId 不属于当前用户与应用");
        }
        int phaseBit = switch (phase == null ? "" : phase) {
            case "request" -> 1;
            case "response" -> 2;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phase 只能是 request 或 response");
        };
        if ((trace.phases() & phaseBit) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该 trace 阶段已上报");
        }

        LoggerEvent event = new LoggerEvent()
                .setTs(now)
                .setApp(app)
                .setInstance(LoggerAutoConfiguration.instanceId())
                .setLevel("INFO")
                .setLogger("logger-sdk.trace")
                .setThread("browser")
                .setTraceId(traceId)
                .setMessage("request".equals(phase) ? "SDK request started" : "SDK request completed")
                .setMdc(Map.of(
                        "phase", phase,
                        "method", safe(method, 16),
                        "path", safe(path, 512),
                        "status", status == null ? "" : String.valueOf(status),
                        "durationMs", durationMs == null ? "" : String.valueOf(Math.max(0L, durationMs))));
        store.append(List.of(event));
        bus.publish(List.of(event));
        if (!stateStore.updateTracePhases(traceId, trace.phases(), trace.phases() | phaseBit)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该 trace 阶段已上报或状态已变更");
        }
    }

    public synchronized List<Map<String, Object>> blocked() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> result = new ArrayList<>();
        for (LoggerTraceStateStore.RateState state : stateStore.findBlocked(now, 500)) {
            result.add(Map.of("userId", state.userId(), "app", state.app(),
                    "blockedUntil", state.blockedUntil()));
        }
        return result;
    }

    public synchronized int unblock(String scope, String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "解除目标不能为空");
        }
        if (!"app".equals(scope) && !"user".equals(scope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope 只能是 app 或 user");
        }
        return stateStore.clearBlocked(scope, value, System.currentTimeMillis());
    }

    private ResponseStatusException blocked(long until) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "SDK 请求频率超限，已封锁 30 分钟；blockedUntil=" + until);
    }

    private static String safe(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }
}
