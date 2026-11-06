package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectEngine;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.CollectFunctions;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 过滤节点：按条件决定当前分支是否继续执行。
 *
 * <p>替代原「脚本节点」的轻量场景：不需要写 Groovy，只做「值不为空就跳过、为空就继续」
 * 这类判断。上一个节点的数据通过 {@code ${data}} 引用，判断表达式支持简单比较与逻辑运算。</p>
 *
 * <p>执行语义：</p>
 * <ul>
 *   <li>表达式求值为真 → 放行，继续向下游派发令牌；</li>
 *   <li>表达式求值为假 → 本令牌终止，不再向下游派发（等价于「结束当前分支」）。</li>
 * </ul>
 *
 * <p>典型用法：接在「查已存在」节点后面，用 {@code ${data.freshCount > 0}} 判断是否有全新数据，
 * 有则继续写入、没有则直接结束分支。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class CollectFilterExecutor implements CollectShapeExecutor {

    /**
     * 判断表达式参数名。
     */
    public static final String EXPRESSION = "expression";

    /**
     * 输出名参数名。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 默认输出变量名。
     */
    public static final String DEFAULT_OUTPUT = "data";

    @Override
    public String supportShape() {
        return "filter";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "过滤", "控制", "filter",
                "按条件决定当前分支是否继续：条件为真放行，为假结束当前分支");
        meta.setOutputVariable("默认 data，过滤不改变数据本身，只决定是否继续向下游执行");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(EXPRESSION, "放行条件", "textarea",
                        "布尔表达式，裸写或 ${} 包裹都可以。为真放行继续执行，为假结束当前分支。"
                                + "例：${data.freshCount > 0}、${word != null}、${rows.size() > 0}"),
                CollectShapeMetas.outputVariable("过滤结果写入的变量名，默认 data"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        String raw = node.param(EXPRESSION);
        if (raw.isEmpty()) {
            throw new IllegalStateException("过滤节点没有填写放行条件");
        }
        // 走 evalExpr 而不是 eval：裸文本也按表达式解析，避免「1 == 2」被当成非空串判为真
        Object value = CollectExpression.evalExpr(raw, variables);
        boolean pass = CollectExpression.truthy(value);
        String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        // 过滤不改变数据：把当前 data 原样写回输出名，保证下游 ${data} 仍可用
        Object current = variables.get("data");
        if (current != null) {
            variables.put(output, current);
        }
        if (!pass) {
            // 写停止标记：引擎派发时看到 _stop=true 就不再向下游发令牌
            variables.put(CollectEngine.VAR_STOP, Boolean.TRUE);
        }
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        if (node.param(EXPRESSION).isEmpty()) {
            return null;
        }
        Object stop = variables.get(CollectEngine.VAR_STOP);
        boolean stopped = Boolean.TRUE.equals(stop);
        return (stopped ? "拦截" : "放行") + " · " + node.param(EXPRESSION);
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return DEFAULT_OUTPUT;
    }
}
