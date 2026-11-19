// [P3C A20 豁免] CDP 事件方法名（Runtime.* / Network.*）为协议字符串常量，豁免魔法值。
package com.chua.starter.cdp;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CDP 控制台 / 网络事件环形缓冲
 *
 * <p>调试中心的实时增量统一走 SSE（{@code /ms/cdp/events}）；本缓冲用于「一次性拉取」：
 * 前端刷新 / 重连后可以通过 {@code GET /ms/cdp/console}、{@code GET /ms/cdp/network}
 * 把最近的事件补齐，不必为了翻历史日志而常驻一个 DevTools 会话。</p>
 *
 * <p>引擎在事件分发时同步写入：控制台类只收 {@code Runtime.consoleAPICalled} /
 * {@code Runtime.exceptionThrown} / {@code Runtime.bindingCalled}，网络类收全部
 * {@code Network.*} 事件。环形结构，单类最多保留 {@value #MAX_ENTRIES} 条，
 * 超出挤掉最旧的。浏览器重启 / 停止时由 {@code CdpEngineService} 调 {@link #clear()}
 * 清空——旧页面的事件已经没有意义。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Component
public class CdpEventBuffer {

    /**
     * 单类事件的最大保留条数（环形覆盖最旧的）
     */
    // [P3C A20 豁免]
    private static final int MAX_ENTRIES = 500;

    /**
     * 默认拉取条数
     */
    // [P3C A20 豁免]
    private static final int DEFAULT_LIMIT = 200;

    /**
     * 控制台类事件方法
     */
    private static final List<String> CONSOLE_METHODS = List.of(
            "Runtime.consoleAPICalled",
            "Runtime.exceptionThrown",
            "Runtime.bindingCalled"
    );

    /**
     * 控制台事件（旧 → 新）
     */
    private final Deque<Map<String, Object>> consoleEvents = new ArrayDeque<>();

    /**
     * 网络事件（旧 → 新）
     */
    private final Deque<Map<String, Object>> networkEvents = new ArrayDeque<>();

    /**
     * 记录一条 CDP 事件（只保留控制台与网络两类，其余忽略）
     *
     * @param event 分发中的事件（method / params / targetId）
     */
    public void record(Map<String, Object> event) {
        if (event == null) {
            return;
        }
        Object method = event.get("method");
        if (!(method instanceof String name) || name.isBlank()) {
            return;
        }
        if (CONSOLE_METHODS.contains(name)) {
            append(consoleEvents, event);
        } else if (name.startsWith("Network.")) {
            append(networkEvents, event);
        }
    }

    /**
     * 拉取控制台事件（按发生顺序，旧 → 新）
     *
     * @param targetId 页面标识，空表示全部页面
     * @param limit    最多返回条数（取最新的），非正数时取默认值
     * @return 事件快照
     */
    public List<Map<String, Object>> console(String targetId, int limit) {
        return tail(consoleEvents, targetId, limit);
    }

    /**
     * 拉取网络事件（按发生顺序，旧 → 新）
     *
     * @param targetId 页面标识，空表示全部页面
     * @param limit    最多返回条数（取最新的），非正数时取默认值
     * @return 事件快照
     */
    public List<Map<String, Object>> network(String targetId, int limit) {
        return tail(networkEvents, targetId, limit);
    }

    /**
     * 清空缓冲（浏览器重启 / 停止时调用）
     */
    public synchronized void clear() {
        consoleEvents.clear();
        networkEvents.clear();
    }

    /**
     * 追加一条事件并做环形淘汰
     *
     * <p>只挑出消费方需要的字段重新组装，不持有分发过程中的原始引用；
     * 并统一补上服务端时间戳，便于跨页面排序与展示。</p>
     *
     * @param deque 目标队列
     * @param event 原始事件
     */
    private void append(Deque<Map<String, Object>> deque, Map<String, Object> event) {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("method", event.get("method"));
        stored.put("params", event.get("params"));
        stored.put("targetId", event.get("targetId"));
        stored.put("timestamp", System.currentTimeMillis());
        synchronized (this) {
            while (deque.size() >= MAX_ENTRIES) {
                deque.pollFirst();
            }
            deque.addLast(stored);
        }
    }

    /**
     * 取队尾若干条（可按页面过滤）
     *
     * @param deque    事件队列
     * @param targetId 页面标识，空表示全部
     * @param limit    最多返回条数
     * @return 按发生顺序（旧 → 新）的事件列表
     */
    private List<Map<String, Object>> tail(Deque<Map<String, Object>> deque, String targetId, int limit) {
        int count = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_ENTRIES);
        List<Map<String, Object>> matched = new ArrayList<>();
        synchronized (this) {
            for (Map<String, Object> event : deque) {
                if (targetId == null || targetId.isBlank()
                        || targetId.equals(String.valueOf(event.get("targetId")))) {
                    matched.add(event);
                }
            }
        }
        if (matched.size() <= count) {
            return matched;
        }
        return new ArrayList<>(matched.subList(matched.size() - count, matched.size()));
    }
}
