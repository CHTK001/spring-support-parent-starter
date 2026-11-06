package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 注释节点：不产生任何执行效果。
 *
 * <p>用于在设计器里标注流程意图，或作为连线的中转点把两条分支画在同一个位置。
 * 注释文本会出现在节点日志的摘要里，便于回看时理解当时的设计意图。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class CollectCommentExecutor implements CollectShapeExecutor {

    /**
     * 注释内容参数名。
     */
    public static final String TEXT = "comment";

    @Override
    public String supportShape() {
        return "comment";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "注释", "控制", "note",
                "只做说明，不影响数据");
        return CollectShapeMetas.with(meta,
                CollectParamMeta.of(TEXT, "注释内容", "textarea"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        // 注释节点没有副作用，摘要由 summary() 提供
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        String text = node.param(TEXT);
        return text.isEmpty() ? "注释" : text;
    }
}
