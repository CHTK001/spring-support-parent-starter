package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
* 通用占位节点。
*
* <p>用于尚未实现业务逻辑的节点类型（script/db/function 等），
* 执行时仅透传当前数据，保证流程可完整跑通，不因缺实现而中断。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Component
public class SpiderNoopNode implements FlowNode {

    /**
    * 日志记录器
    */
    private static final Logger log = LoggerFactory.getLogger(SpiderNoopNode.class);

    /**
    * 节点类型标识（被注册到多个类型下）。
    *
    * @return 节点类型
    */
    @Override
    public String type() {
        return "spider.noop";
    }

    /**
    * 节点配置表单元信息。
    *
    * @return 空配置（占位节点无需配置）
    */
    @Override
    public List<FlowNodeField> configSchema() {
        return new ArrayList<>();
    }

    /**
    * 执行占位节点：仅记录日志，数据透传。
    *
    * @param context 当前流程上下文
    */
    @Override
    public void execute(FlowContext context) {
        log.info("spider.noop 节点 {} 透传执行", context.getCurrentNodeId());
    }
}
