package com.chua.starter.spider.flow.handler;

import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.flow.NodeHandler;
import com.chua.starter.spider.flow.SpiderFlowContext;
import com.chua.starter.spider.flow.SpiderFlowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 终止节点处理器：标记流程结束。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class StopNodeHandler implements NodeHandler {

    /**
     * 节点类型编码
     */
    private static final String TYPE = "stop";

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public String execute(SpiderFlowNode node, SpiderFlowContext context) {
        log.info("[Flow {}] 终止节点: {}", context.getExecutionNo(), node.getFlowNodeName());
        return "stopped";
    }
}


