package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectDedupStore;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.CollectEngine;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 去重节点：已采过的业务主键直接终止本分支。
 *
 * <p>判重依据是采集结果表里已有的主键值，因此必须与输出节点使用同一个主键表达式，
 * 典型接法为 抓取 → 去重(${url}) → 抽取 → 输出(主键 ${url})。
 * 命中重复时置 {@code _stop}，本令牌不再派发下游，但兄弟分支不受影响。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectDedupExecutor implements CollectShapeExecutor {

    /**
     * 主键表达式参数名。
     */
    public static final String KEY = "key";

    /**
     * 判重范围参数名。
     */
    public static final String SCOPE = "scope";

    /**
     * 仅本流程判重。
     */
    public static final String SCOPE_FLOW = "flow";

    /**
     * 跨流程判重。
     */
    public static final String SCOPE_GLOBAL = "global";

    /**
     * 本令牌判重主键的暂存变量名，仅供摘要使用。
     */
    private static final String KEY_VAR = "_dedup";

    /**
     * 判重存储。
     */
    private final CollectDedupStore dedupStore;

    @Override
    public String supportShape() {
        return "dedup";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "去重", "控制", "shield",
                "按业务主键跳过历史已采条目，命中即终止本分支");
        CollectParamMeta scope = CollectShapeMetas.option(SCOPE, "判重范围", SCOPE_FLOW,
                "本流程只在当前流程的结果里判重，跨流程用于多个流程共享同一份数据", SCOPE_FLOW, SCOPE_GLOBAL);
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(KEY, "主键表达式", "text",
                        "例：${url} 或 ${item.id}，须与采集平台节点的主键写法一致"),
                scope);
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        String raw = node.param(KEY);
        if (raw.isEmpty()) {
            throw new IllegalStateException("去重节点没有填写主键表达式");
        }
        String key = CollectExpression.evalText(raw, variables).trim();
        if (key.isEmpty()) {
            throw new IllegalStateException("去重主键求值为空，请检查表达式: " + raw);
        }
        boolean global = SCOPE_GLOBAL.equals(node.param(SCOPE));
        variables.put(KEY_VAR, key);
        if (dedupStore.seen(context.getFlowId(), global, key)) {
            variables.put(CollectEngine.VAR_STOP, Boolean.TRUE);
        }
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        Object stop = variables.get(CollectEngine.VAR_STOP);
        Object key = variables.get(KEY_VAR);
        if (CollectExpression.truthy(stop)) {
            return "已采过，跳过: " + key;
        }
        return "新条目: " + key;
    }
}
