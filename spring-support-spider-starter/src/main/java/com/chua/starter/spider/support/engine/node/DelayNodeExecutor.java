package com.chua.starter.spider.support.engine.node;

import com.chua.starter.spider.support.domain.SpiderFlowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DELAY 节点执行器。
 *
 * <p>支持三种延迟模式（config["delayType"]）：
 * <ul>
 *   <li>{@code fixed}：固定延迟 config["delayMs"] 毫秒（默认 1000）</li>
 *   <li>{@code random}：在 [config["minMs"], config["maxMs"]] 范围内随机延迟（默认 500~2000）</li>
 *   <li>{@code adaptive}：根据上游数据量自适应延迟（暂时等同 fixed）</li>
 * </ul>
 * </p>
 *
 * <p>DELAY 节点为透传节点，延迟结束后原样返回输入数据。</p>
 *
 * @author CH
 */
@Slf4j
public class DelayNodeExecutor {

    /**
     * 执行延迟并透传输入数据。
     *
     * @param node      DELAY 节点定义
     * @param inputData 上游传入的数据（透传）
     * @return 原样返回 inputData
     */
    public Object execute(SpiderFlowNode node, Object inputData) {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String delayType = str(config, "delayType", "fixed");

        long millis = switch (delayType.toLowerCase()) {
            case "fixed"    -> longVal(config, "delayMs", 1000L);
            case "random"   -> {
                long min = longVal(config, "minMs", 500L);
                long max = longVal(config, "maxMs", 2000L);
                yield min >= max ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
            }
            case "adaptive" -> longVal(config, "delayMs", 1000L); // 自适应暂时等同 fixed
            default -> {
                log.warn("[DELAY] nodeId={} 未知 delayType={}，使用默认 1000ms", node.getNodeId(), delayType);
                yield 1000L;
            }
        };

        log.debug("[DELAY] nodeId={} delayType={} 延迟 {}ms", node.getNodeId(), delayType, millis);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[DELAY] nodeId={} 延迟被中断", node.getNodeId());
            }
        }
        return inputData;
    }

    private String str(Map<String, Object> config, String key, String def) {
        Object v = config.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : def;
    }

    private long longVal(Map<String, Object> config, String key, long def) {
        Object v = config.get(key);
        if (v == null) return def;
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return def; }
    }
}
