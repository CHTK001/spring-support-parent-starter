package com.chua.starter.hotsport.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.hotsport.support.service.ApmQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * APM 热点观测 REST 接口。
 *
 * <p>统一前缀 {@code /api/apm}，由前端 vue-support-hotspot-starter 经
 * {@code @repo/utils} 的 {@code http().get('/api/apm/...')} 调用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/api/apm")
@RequiredArgsConstructor
@Slf4j
public class ApmController {

    /** APM 查询服务。 */
    private final ApmQueryService apmQueryService;

    /** JSON 序列化（SSE data 事件）。 */
    private final ObjectMapper objectMapper;

    /** SSE 推送线程池（单线程足够，间隔 3s）。 */
    private final ScheduledExecutorService sseExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "hotsport-sse");
                t.setDaemon(true);
                return t;
            });

    /**
     * 概览统计。
     *
     * @return 各类型总量 + agent 状态
     */
    @GetMapping("/stats")
    public ReturnResult<Map<String, Object>> stats() {
        return ReturnResult.ok(apmQueryService.stats());
    }

    /**
     * 分页查询传输记录。
     *
     * @param page      页码，默认 1
     * @param size      每页条数，默认 20
     * @param source    源端过滤
     * @param target    目标端过滤
     * @param protocol  协议过滤
     * @param errorOnly 仅看错误
     * @param startTime 开始时间（epoch 毫秒）
     * @param endTime   结束时间（epoch 毫秒）
     * @return 分页数据
     */
    @GetMapping("/transmissions")
    public ReturnResult<Map<String, Object>> transmissions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) Boolean errorOnly,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        return ReturnResult.ok(apmQueryService.transmissions(page, size, source, target, protocol, errorOnly, startTime, endTime));
    }

    /**
     * 查询依赖边。
     *
     * @param size 上限，默认 100
     * @return 依赖边列表
     */
    @GetMapping("/dependencies")
    public ReturnResult<Map<String, Object>> dependencies(
            @RequestParam(defaultValue = "100") int size) {
        List<Map<String, Object>> items = apmQueryService.dependencies(size);
        Map<String, Object> result = Map.of("total", items.size(), "items", items);
        return ReturnResult.ok(result);
    }

    /**
     * 分页查询资源泄漏。
     *
     * @param page        页码，默认 1
     * @param size        每页条数，默认 20
     * @param resourceType 类型过滤
     * @param minAgeMs   最小存活毫秒
     * @param startTime  开始时间（epoch 毫秒）
     * @param endTime    结束时间（epoch 毫秒）
     * @return 分页数据
     */
    @GetMapping("/leaks")
    public ReturnResult<Map<String, Object>> leaks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long minAgeMs,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        return ReturnResult.ok(apmQueryService.leaks(page, size, resourceType, minAgeMs, startTime, endTime));
    }

    /**
     * 分页查询日志。
     *
     * @param page  页码，默认 1
     * @param size  每页条数，默认 20
     * @param level 级别过滤
     * @return 分页数据
     */
    @GetMapping("/logs")
    public ReturnResult<Map<String, Object>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String level) {
        return ReturnResult.ok(apmQueryService.logs(page, size, level));
    }

    /**
     * Agent 实时状态。
     *
     * @return agent 是否激活 + Shell 端口等
     */
    @GetMapping("/agent/status")
    public ReturnResult<Map<String, Object>> agentStatus() {
        Map<String, Object> status = apmQueryService.stats();
        return ReturnResult.ok(status);
    }

    // ------------------------------------------------------------------
    // SSE 实时流（增强）
    // ------------------------------------------------------------------

    /**
     * SSE 实时统计流。
     *
     * <p>每 {@code intervalMs} 毫秒推送一次 {@code data} 事件（stats 快照），
     * 前端用 EventSource 接收并实时刷新看板，替代 HTTP 轮询。</p>
     *
     * @param intervalMs 推送间隔（毫秒），默认 3000，钳制到 [1000, 60000]
     * @return SSE 流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "3000") long intervalMs) {
        long effective = Math.max(1000, Math.min(60000, intervalMs));
        SseEmitter emitter = new SseEmitter(5L * 60 * 1000);
        ScheduledFuture<?> task = sseExecutor.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> stats = apmQueryService.stats();
                String json = objectMapper.writeValueAsString(Map.of("ts", System.currentTimeMillis(), "data", stats));
                emitter.send(SseEmitter.event().name("stats").data(json));
            } catch (IOException e) {
                emitter.complete();
            }
        }, 0, effective, TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> task.cancel(true));
        emitter.onTimeout(() -> {
            task.cancel(false);
            emitter.complete();
        });
        return emitter;
    }

    /** 容器销毁时关闭推送线程。 */
    @PreDestroy
    public void shutdownSse() {
        sseExecutor.shutdownNow();
    }
}
