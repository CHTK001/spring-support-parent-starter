package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.engine.node.ErrorHandlerNodeExecutor;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * ERROR_HANDLER 节点 SPI 执行器。
 */
@Slf4j
@Spi("ERROR_HANDLER")
public class ErrorHandlerSpiNodeExecutor implements SpiderNodeExecutor {

    private final ErrorHandlerNodeExecutor delegate = new ErrorHandlerNodeExecutor();

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        // 在正常执行路径中，ERROR_HANDLER 透传数据；错误处理由 SpiderExecutionEngine 调用 delegate.handle()
        return context.inputData();
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.ERROR_HANDLER; }
}
