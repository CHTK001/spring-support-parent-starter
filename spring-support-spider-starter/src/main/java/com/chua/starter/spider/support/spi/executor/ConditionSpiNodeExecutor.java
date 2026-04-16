package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.engine.node.ConditionNodeExecutor;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * CONDITION 节点 SPI 执行器（双端口路由）。
 * 输出 Map 包含 "branch"（true/false）和原始数据。
 */
@Slf4j
@Spi("CONDITION")
public class ConditionSpiNodeExecutor implements SpiderNodeExecutor {

    private final ConditionNodeExecutor delegate = new ConditionNodeExecutor();

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        boolean result = delegate.evaluate(node, context.inputData());
        log.debug("[CONDITION] nodeId={} branch={}", node.getNodeId(), result);
        return Map.of("branch", result, "data", context.inputData() != null ? context.inputData() : Map.of());
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.CONDITION; }
}
