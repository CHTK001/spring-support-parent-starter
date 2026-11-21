package com.chua.starter.logger.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.constant.MdcConstant;
import com.chua.starter.logger.LoggerAutoConfiguration;
import com.chua.starter.logger.event.LoggerEvent;
import com.chua.starter.logger.properties.LoggerProperties;
import com.chua.starter.logger.store.LoggerEventBus;
import com.chua.starter.logger.store.LoggerEventStore;
import com.chua.starter.logger.store.LoggerPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 日志检索 API（{@code /v2/logger}）。
 *
 * <p>全部端点返回统一 {@link ReturnResult} 信封；{@code /stream} 为 SSE 长连。
 * 另提供浏览器 JS 推送入口：{@code GET /sdk.js} 加载零依赖 SDK，
 * {@code POST /ingest} 批量接收前端日志事件（与 TCP 通道同构：入库 + SSE 实时转发）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@RestController
@RequestMapping("/v2/logger")
public class LoggerController {

    /**
     * 单页上限
     */
    private static final int MAX_LIMIT = 500;

    /**
     * 单次推送批量上限
     */
    private static final int MAX_BATCH = 1000;

    /**
     * 链路摘要单页上限
     */
    private static final int MAX_TRACE_LIMIT = 200;

    /**
     * 浏览器 SDK 脚本资源路径
     */
    private static final String SDK_RESOURCE = "com/chua/starter/logger/logger-sdk.js";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LoggerEventStore store;
    private final LoggerSseBroker broker;
    private final LoggerEventBus bus;
    private final LoggerProperties properties;
    private final String defaultApp;

    /**
     * @param store       日志存储
     * @param broker      SSE 推送
     * @param bus         事件总线（ingest 后转发，与 TCP 通道同构）
     * @param properties  模块配置
     * @param environment 容器环境（解析缺省应用名）
     */
    public LoggerController(LoggerEventStore store, LoggerSseBroker broker, LoggerEventBus bus,
                            LoggerProperties properties, Environment environment) {
        this.store = store;
        this.broker = broker;
        this.bus = bus;
        this.properties = properties;
        this.defaultApp = LoggerAutoConfiguration.resolveAppName(properties, environment);
    }

    /**
     * 应用/实例清单
     *
     * @return 清单
     */
    @GetMapping("/apps")
    public ReturnResult<List<Map<String, Object>>> apps() {
        return ReturnResult.list(store.apps());
    }

    /**
     * 分页查询日志
     *
     * @param app      应用过滤
     * @param instance 实例过滤
     * @param level    级别过滤（逗号分隔多值）
     * @param keyword  关键字
     * @param traceId  链路追踪标识过滤
     * @param start    起始时间（毫秒）
     * @param end      结束时间（毫秒）
     * @param cursor   翻页游标
     * @param limit    每页条数（1~500）
     * @return 分页结果
     */
    @GetMapping("/events")
    public ReturnResult<LoggerPage> events(@RequestParam(required = false) String app,
                                           @RequestParam(required = false) String instance,
                                           @RequestParam(required = false) String level,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String traceId,
                                           @RequestParam(required = false) Long start,
                                           @RequestParam(required = false) Long end,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return ReturnResult.ok(store.query(app, instance, level, keyword, traceId, start, end, cursor, safeLimit));
    }

    /**
     * 链路追踪摘要：按 traceId 聚合近期事件（条数/错误数/起止/涉及应用）
     *
     * @param app   应用过滤
     * @param limit 返回条数上限（1~200）
     * @return 摘要列表
     */
    @GetMapping("/traces")
    public ReturnResult<List<Map<String, Object>>> traces(@RequestParam(required = false) String app,
                                                          @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_TRACE_LIMIT));
        return ReturnResult.list(store.traces(app, safeLimit));
    }

    /**
     * 统计信息（total/levels/eps/topLoggers/apps/instances）
     *
     * @param app      应用过滤
     * @param instance 实例过滤
     * @return 统计数据
     */
    @GetMapping("/stats")
    public ReturnResult<Map<String, Object>> stats(@RequestParam(required = false) String app,
                                                   @RequestParam(required = false) String instance) {
        return ReturnResult.ok(store.stats(app, instance));
    }

    /**
     * 删除事件
     *
     * @param app      应用过滤
     * @param instance 实例过滤
     * @param before   删除该时间（毫秒）之前的事件
     * @return 删除条数
     */
    @DeleteMapping("/events")
    public ReturnResult<Integer> delete(@RequestParam(required = false) String app,
                                        @RequestParam(required = false) String instance,
                                        @RequestParam(required = false) Long before) {
        return ReturnResult.ok(store.delete(app, instance, before));
    }

    /**
     * 浏览器/JS 日志推送入口。请求体支持三种形态：事件数组、单事件对象、
     * {@code {"events":[...]}} 包装；单批上限 {@value #MAX_BATCH} 条。
     *
     * <p>缺省补齐：ts（当前时间）、level（INFO）、app（x-logger-app 头 → 宿主应用名）、
     * instance（服务端实例标识）、traceId（请求头 x-trace-id，与后端 MDC 链路对齐）。
     * 可选共享密钥：{@code plugin.logger.server.ingest-token} 非空时校验
     * {@code x-logger-token} 请求头。</p>
     *
     * @param request 请求（取头）
     * @param body    事件载荷
     * @return 接收条数
     */
    @PostMapping(value = "/ingest", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReturnResult<Integer> ingest(HttpServletRequest request,
                                        @RequestBody(required = false) JsonNode body) {
        String expected = properties.getServer().getIngestToken();
        if (expected != null && !expected.isBlank()) {
            String token = request.getHeader("x-logger-token");
            if (!expected.equals(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ingest token 不匹配");
            }
        }
        List<LoggerEvent> events = parseEvents(body);
        if (events.isEmpty()) {
            return ReturnResult.ok(0);
        }
        String headerApp = request.getHeader("x-logger-app");
        String headerTraceId = request.getHeader(MdcConstant.X_TRACE_ID);
        long now = System.currentTimeMillis();
        for (LoggerEvent event : events) {
            if (event.getTs() <= 0) {
                event.setTs(now);
            }
            if (event.getApp() == null || event.getApp().isBlank()) {
                event.setApp(headerApp == null || headerApp.isBlank() ? defaultApp : headerApp.trim());
            }
            if (event.getInstance() == null || event.getInstance().isBlank()) {
                event.setInstance(LoggerAutoConfiguration.instanceId());
            }
            if (event.getLevel() == null || event.getLevel().isBlank()) {
                event.setLevel("INFO");
            }
            if ((event.getTraceId() == null || event.getTraceId().isBlank())
                    && headerTraceId != null && !headerTraceId.isBlank()) {
                event.setTraceId(headerTraceId);
            }
        }
        try {
            store.append(events);
        } catch (Exception ignored) {
            // 入库失败不影响实时转发
        }
        bus.publish(events);
        return ReturnResult.ok(events.size());
    }

    /**
     * 浏览器 JS SDK（零依赖 UMD 脚本）：{@code <script src="/v2/logger/sdk.js"></script>}
     * 后调 {@code LoggerJS.init({...})} 即可采集 console/异常并批量推送。
     *
     * @throws IOException 资源读取失败
     */
    @GetMapping(value = "/sdk.js", produces = "application/javascript;charset=utf-8")
    public void sdk(HttpServletResponse response) throws IOException {
        response.setContentType("application/javascript;charset=utf-8");
        response.setHeader("Cache-Control", "no-cache");
        try (InputStream in = new ClassPathResource(SDK_RESOURCE).getInputStream()) {
            in.transferTo(response.getOutputStream());
        }
    }

    /**
     * 实时日志流（SSE；事件名 log / heartbeat / open）。
     * 订阅可带过滤条件（与 /events 同口径），服务端过滤后再推送。
     *
     * @param app      应用过滤
     * @param instance 实例过滤
     * @param level    级别过滤（逗号分隔多值）
     * @param keyword  关键字过滤
     * @param traceId  链路追踪标识过滤
     * @return SSE emitter
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String app,
                             @RequestParam(required = false) String instance,
                             @RequestParam(required = false) String level,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String traceId) {
        return broker.subscribe(app, instance, level, keyword, traceId);
    }

    /**
     * 解析推送载荷为数组：兼容 [事件...]、单事件、{"events":[...]} 三种形态；
     * 单条解析失败跳过，不阻断整批。
     */
    private List<LoggerEvent> parseEvents(JsonNode body) {
        List<LoggerEvent> events = new ArrayList<>();
        if (body == null || body.isNull()) {
            return events;
        }
        JsonNode array = body;
        if (body.isObject() && body.has("events") && body.get("events").isArray()) {
            array = body.get("events");
        }
        if (array.isArray()) {
            for (JsonNode node : array) {
                if (events.size() >= MAX_BATCH) {
                    break;
                }
                LoggerEvent event = toEvent(node);
                if (event != null) {
                    events.add(event);
                }
            }
            return events;
        }
        LoggerEvent single = toEvent(body);
        if (single != null) {
            events.add(single);
        }
        return events;
    }

    private LoggerEvent toEvent(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return MAPPER.treeToValue(node, LoggerEvent.class);
        } catch (Exception e) {
            return null;
        }
    }
}
