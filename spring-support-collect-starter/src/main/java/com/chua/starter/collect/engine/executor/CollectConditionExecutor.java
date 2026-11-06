package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
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
 * 判断节点：求值一个表达式，把结果写进变量，由出边的流转条件做真假分流。
 *
 * <p>条件分支是<strong>边级</strong>语义，不由本节点另造一套：本节点只负责把
 * {@link #EXPRESSION} 求值成一个结果并写入 {@link #OUTPUT} 指定的变量，
 * 下游多条出边各自用连线上的 {@code condition} 表达式引用该变量完成分流，例如
 * {@code ${conditionResult}} 与 {@code ${!conditionResult}}。引擎在派发时按
 * {@code condition} 的真假决定边是否放行，设计器据 {@code condition} 把边着色为条件分支，
 * 因此无需改动引擎与前端。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class CollectConditionExecutor implements CollectShapeExecutor {

    /**
     * 判断表达式参数名。
     */
    public static final String EXPRESSION = "expression";

    /**
     * 结果变量名参数名。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 结果变量默认名。
     */
    public static final String DEFAULT_OUTPUT = "conditionResult";

    @Override
    public String supportShape() {
        return "condition";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "判断", "控制", "branch",
                "按表达式求值写入变量，出边用条件表达式真假分流");
        meta.setOutputVariable("默认 data，旧流程留空时按 ${conditionResult} 兜底");
        CollectParamMeta output = CollectShapeMetas.outputVariable("求值结果写入该变量，出边条件用 ${输出名} 判断真假");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(EXPRESSION, "判断表达式", "textarea",
                        "布尔表达式。裸写或 ${} 包裹都可以：1 == 2、${status == 1}、${rows.size() > 0}。"
                                + "求值结果写入结果变量，出边条件用 ${结果变量名} 分流；"
                                + "写不成表达式时会直接报错，不会静默判真"),
                output);
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        String raw = node.param(EXPRESSION);
        if (raw.isEmpty()) {
            throw new IllegalStateException("判断节点没有填写判断表达式");
        }
        // 必须走 evalExpr 而不是 eval：eval 对不含 ${} 的文本原样返回字符串，
        // 那样「1 == 2」会被当成非空串判为真 —— 假条件走真分支且毫无提示（验收实测过）。
        // evalExpr 让裸表达式与 ${表达式} 等价；写不成表达式时它会抛可读错误，不静默给错分支。
        Object value = CollectExpression.evalExpr(raw, variables);
        String name = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT);
        variables.put(name, value);
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        if (node.param(EXPRESSION).isEmpty()) {
            return null;
        }
        String name = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT);
        Object value = variables.get(name);
        return (CollectExpression.truthy(value) ? "真" : "假") + " · " + name + "="
                + CollectFunctions.stringify(value);
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return DEFAULT_OUTPUT;
    }
}
