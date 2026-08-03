package com.chua.starter.spider.flow;

import com.chua.starter.spider.entity.SpiderFlowNode;

/**
 * 流程节点执行器抽象。
 *
 * <p>每个 {@link com.chua.starter.spider.enums.SpiderNodeType} 对应一个 Handler 实现，
 * 负责：解析节点 config JSON、执行节点逻辑、把结果写入 {@link SpiderFlowContext}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface NodeHandler {

    /**
     * 返回本 Handler 支持的节点类型。
     *
     * @return 节点类型编码（与 {@link com.chua.starter.spider.enums.SpiderNodeType#getCode()} 一致）
     */
    String supportedType();

    /**
     * 执行节点逻辑。
     *
     * @param node    当前节点（含 config JSON）
     * @param context 流程上下文（变量读写）
     * @return 节点执行结果摘要（用于调试日志）
     * @throws Exception 业务异常
     */
    String execute(SpiderFlowNode node, SpiderFlowContext context) throws Exception;
}
