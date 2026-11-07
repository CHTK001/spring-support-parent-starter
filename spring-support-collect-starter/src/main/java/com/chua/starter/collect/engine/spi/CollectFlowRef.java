package com.chua.starter.collect.engine.spi;

import com.chua.starter.collect.dto.CollectDslDTO;

/**
 * 子流程定义：按流程ID取到的可执行内容。
 *
 * @param flowId   流程ID
 * @param flowName 流程名称，用于节点日志与摘要
 * @param dsl      流程图
 * @author CH
 * @since 4.0.0.51
 */
public record CollectFlowRef(Integer flowId, String flowName, CollectDslDTO dsl) {
}
