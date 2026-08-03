package com.chua.starter.spider.flow.handler;

import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.flow.NodeHandler;
import com.chua.starter.spider.flow.SpiderFlowContext;
import com.chua.starter.spider.flow.SpiderFlowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 起始节点处理器：把流程入口参数写入 ctx.vars，作为后续节点的初始变量。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class StartNodeHandler implements NodeHandler {

    /**
     * 起始节点类型编码
     */
    private static final String TYPE = "start";

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public String execute(SpiderFlowNode node, SpiderFlowContext context) {
        log.info("[Flow {}] 起始节点: {}", context.getExecutionNo(), node.getFlowNodeName());
        context.putVar("_startedAt", System.currentTimeMillis());
        return "started";
    }
}


