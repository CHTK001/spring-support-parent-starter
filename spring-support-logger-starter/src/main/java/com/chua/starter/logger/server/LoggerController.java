package com.chua.starter.logger.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.logger.store.LoggerEventStore;
import com.chua.starter.logger.store.LoggerPage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 日志检索 API（{@code /v2/logger}）。
 *
 * <p>全部端点返回统一 {@link ReturnResult} 信封；{@code /stream} 为 SSE 长连。</p>
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

    private final LoggerEventStore store;
    private final LoggerSseBroker broker;

    /**
     * @param store  日志存储
     * @param broker SSE 推送
     */
    public LoggerController(LoggerEventStore store, LoggerSseBroker broker) {
        this.store = store;
        this.broker = broker;
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
                                           @RequestParam(required = false) Long start,
                                           @RequestParam(required = false) Long end,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return ReturnResult.ok(store.query(app, instance, level, keyword, start, end, cursor, safeLimit));
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
     * 实时日志流（SSE；事件名 log / heartbeat / open）
     *
     * @return SSE emitter
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broker.subscribe();
    }
}
