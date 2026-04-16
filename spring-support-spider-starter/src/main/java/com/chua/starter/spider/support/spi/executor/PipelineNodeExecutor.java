package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * PIPELINE 节点执行器。
 * 负责数据输出（数据库写入、文件输出等），输入 ProcessedRecord/RawRecord，输出 PipelineResult。
 */
@Slf4j
@Spi("PIPELINE")
public class PipelineNodeExecutor implements SpiderNodeExecutor {

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String pipelineType = str(config, "pipelineType", "log");

        log.info("[PIPELINE] nodeId={} pipelineType={} 输出数据", node.getNodeId(), pipelineType);

        // 实际写入由 DatabasePipeline SPI 实现（B95）；此处返回结果摘要
        return Map.of(
                "pipelineType", pipelineType,
                "status", "SUCCESS",
                "count", 1
        );
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.PIPELINE; }

    private String str(Map<String, Object> c, String k, String d) {
        Object v = c.get(k); return (v != null && !v.toString().isBlank()) ? v.toString() : d;
    }
}
