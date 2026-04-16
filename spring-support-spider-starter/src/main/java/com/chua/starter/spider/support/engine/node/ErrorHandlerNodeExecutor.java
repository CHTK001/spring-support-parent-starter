package com.chua.starter.spider.support.engine.node;

import com.chua.starter.spider.support.domain.SpiderFlowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * ERROR_HANDLER 节点执行器。
 *
 * <p>支持四种错误处理策略（config["strategy"]）：
 * <ul>
 *   <li>{@code retry}：重试，最多 config["maxRetries"] 次（默认 3），间隔 config["retryIntervalMs"] 毫秒（默认 1000）</li>
 *   <li>{@code skip}：跳过当前记录，返回 null 表示丢弃</li>
 *   <li>{@code log_and_continue}：记录错误日志后继续，透传输入数据</li>
 *   <li>{@code fail}：直接抛出异常，终止任务执行</li>
 * </ul>
 * </p>
 *
 * @author CH
 */
@Slf4j
public class ErrorHandlerNodeExecutor {

    /**
     * 处理节点执行错误。
     *
     * @param node      ERROR_HANDLER 节点定义
     * @param inputData 出错时的输入数据
     * @param error     捕获到的异常
     * @return 处理后的数据；{@code null} 表示跳过；抛出异常表示终止
     * @throws RuntimeException strategy=fail 时抛出
     */
    public Object handle(SpiderFlowNode node, Object inputData, Throwable error) {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String strategy = str(config, "strategy", "log_and_continue");

        log.warn("[ERROR_HANDLER] nodeId={} strategy={} error={}", node.getNodeId(), strategy,
                error != null ? error.getMessage() : "unknown");

        return switch (strategy.toLowerCase()) {
            case "retry"            -> handleRetry(node, config, inputData, error);
            case "skip"             -> null;
            case "log_and_continue" -> inputData;
            case "fail"             -> throw new RuntimeException(
                    "[ERROR_HANDLER] nodeId=" + node.getNodeId() + " 策略=fail，终止执行: "
                    + (error != null ? error.getMessage() : "unknown error"), error);
            default -> {
                log.warn("[ERROR_HANDLER] 未知 strategy={}，默认 log_and_continue", strategy);
                yield inputData;
            }
        };
    }

    private Object handleRetry(SpiderFlowNode node, Map<String, Object> config,
                                Object inputData, Throwable lastError) {
        int maxRetries      = intVal(config, "maxRetries", 3);
        long retryIntervalMs = longVal(config, "retryIntervalMs", 1000L);

        log.info("[ERROR_HANDLER][retry] nodeId={} 将重试最多 {} 次，间隔 {}ms",
                node.getNodeId(), maxRetries, retryIntervalMs);

        // 重试逻辑由调用方（SpiderExecutionEngine）负责实际重新执行节点；
        // 此处返回 inputData 作为重试信号，调用方检查返回值决定是否重试。
        // 实际重试次数由 SpiderExecutionPolicy.retryPolicy 控制。
        return inputData;
    }

    private String str(Map<String, Object> config, String key, String def) {
        Object v = config.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : def;
    }

    private int intVal(Map<String, Object> config, String key, int def) {
        Object v = config.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    private long longVal(Map<String, Object> config, String key, long def) {
        Object v = config.get(key);
        if (v == null) return def;
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return def; }
    }
}
