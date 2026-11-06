package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 结束节点：显式收束流程。
 *
 * <p>本身不产生副作用，作用是把"流程走完了"这件事画出来并交给结构校验把关：
 * 每条分支的末端必须是结束节点，避免抓取到一半悄悄断掉。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class CollectEndExecutor implements CollectShapeExecutor {

    @Override
    public String supportShape() {
        return "end";
    }

    @Override
    public CollectShapeMeta meta() {
        return CollectShapeMetas.of(supportShape(), "结束", "输出", "stop",
                "流程终点，每条分支都必须收到结束节点");
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        // 终点没有副作用，摘要由 summary() 提供
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        return "流程结束";
    }
}
