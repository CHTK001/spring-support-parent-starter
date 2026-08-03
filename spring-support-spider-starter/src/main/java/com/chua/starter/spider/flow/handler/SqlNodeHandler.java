package com.chua.starter.spider.flow.handler;

import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.flow.NodeHandler;
import com.chua.starter.spider.flow.SpiderFlowContext;
import com.chua.starter.spider.flow.SpiderFlowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 裸 SQL 节点处理器：语义上等同 {@link DbNodeHandler} 但 operation 强制为 update。
 *
 * <p>实际委托给 {@link DbNodeHandler} 处理。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class SqlNodeHandler implements NodeHandler {

    /**
     * 节点类型编码
     */
    private static final String TYPE = "sql";

    private final DbNodeHandler dbHandler;

    public SqlNodeHandler(DbNodeHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public String execute(SpiderFlowNode node, SpiderFlowContext context) throws Exception {
        // 委托给 DB 处理器（约定 Sql 节点 operation 视为 update）
        return dbHandler.execute(node, context);
    }
}


