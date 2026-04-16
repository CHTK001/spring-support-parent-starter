package com.chua.starter.spider.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;

/**
 * 节点执行器 SPI 接口。
 *
 * <p>每种节点类型对应一个实现，通过 {@link ServiceProvider} 按节点类型名称动态加载。
 * 实现类需标注 {@code @Spi("NODE_TYPE_NAME")}，例如 {@code @Spi("DOWNLOADER")}。</p>
 *
 * <p>执行上下文通过 {@link SpiderNodeExecutionContext} 传入，包含：
 * <ul>
 *   <li>节点定义（config、aiProfile 等）</li>
 *   <li>上游输入数据</li>
 *   <li>任务 ID、执行记录 ID</li>
 * </ul>
 * </p>
 *
 * @author CH
 */
public interface SpiderNodeExecutor {

    /**
     * 执行节点逻辑。
     *
     * @param node    当前节点定义
     * @param context 执行上下文（含输入数据、任务信息等）
     * @return 节点输出数据，传递给下游节点；返回 {@code null} 表示跳过后续节点
     * @throws Exception 节点执行失败时抛出
     */
    Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception;

    /**
     * 返回此执行器支持的节点类型。
     *
     * @return 节点类型枚举
     */
    SpiderNodeType supportedType();

    /**
     * 节点执行上下文。
     */
    record SpiderNodeExecutionContext(
            Long taskId,
            Long recordId,
            Object inputData,
            java.util.Map<String, Object> taskConfig
    ) {}
}
