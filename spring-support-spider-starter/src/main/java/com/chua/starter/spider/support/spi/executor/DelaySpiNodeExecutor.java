package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.engine.node.DelayNodeExecutor;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * DELAY 节点 SPI 执行器（透传，插入延迟）。
 */
@Slf4j
@Spi("DELAY")
public class DelaySpiNodeExecutor implements SpiderNodeExecutor {

    private final DelayNodeExecutor delegate = new DelayNodeExecutor();

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        return delegate.execute(node, context.inputData());
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.DELAY; }
}
