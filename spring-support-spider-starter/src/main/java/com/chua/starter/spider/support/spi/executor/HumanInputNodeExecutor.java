package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.engine.HumanInputSuspendRegistry;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HUMAN_INPUT 节点执行器。
 * 挂起执行等待用户输入，输入/输出均为透传。
 */
@Slf4j
@Spi("HUMAN_INPUT")
@RequiredArgsConstructor
public class HumanInputNodeExecutor implements SpiderNodeExecutor {

    private final HumanInputSuspendRegistry registry;

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        int timeoutSeconds = intVal(config, "timeoutSeconds", 300);
        String onTimeout = str(config, "onTimeout", "fail");

        log.info("[HUMAN_INPUT] nodeId={} taskId={} 进入等待输入", node.getNodeId(), context.taskId());

        CompletableFuture<String> future = registry.register(context.taskId(), node.getNodeId());
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            registry.complete(context.taskId(), node.getNodeId(), null);
            if ("skip".equalsIgnoreCase(onTimeout)) return context.inputData();
            throw new RuntimeException("HUMAN_INPUT 节点 [" + node.getNodeId() + "] 等待超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HUMAN_INPUT 节点 [" + node.getNodeId() + "] 被中断", e);
        }
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.HUMAN_INPUT; }

    private String str(Map<String, Object> c, String k, String d) {
        Object v = c.get(k); return (v != null && !v.toString().isBlank()) ? v.toString() : d;
    }

    private int intVal(Map<String, Object> c, String k, int d) {
        Object v = c.get(k); if (v == null) return d;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return d; }
    }
}
