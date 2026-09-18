package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
* 脚本节点（spel 表达式）。
*
* <p>使用 Spring Expression Language 计算一个表达式，并将结果写入上下文属性，
* 用于在不写额外 Java 代码的前提下完成拼接、计算与条件取值等逻辑。</p>
*
* <p>节点配置说明：</p>
* <ul>
*   <li>{@code expression} SpEL 表达式（必填），上下文属性以 {@code #属性名} 引用，
*       当前处理数据可直接以 {@code #data} 访问</li>
*   <li>{@code saveAs} 结果写入的上下文属性名（必填）</li>
* </ul>
*
* <p>示例：{@code expression = "#title.toUpperCase() + '#' + #code"}</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Component
public class SpiderScriptNode implements FlowNode {

    /**
    * 当前数据的上下文变量名
    */
    private static final String VAR_DATA = "data";

    /**
    * spel 表达式解析器
    */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
    * 节点类型标识。
    *
    * @return 节点类型
    */
    @Override
    public String type() {
        return "spider.script";
    }

    /**
    * 节点配置表单元信息。
    *
    * @return 表单元信息列表
    */
    @Override
    public List<FlowNodeField> configSchema() {
        List<FlowNodeField> schema = new ArrayList<>();
        schema.add(FlowNodeField.textarea("expression", "SpEL 表达式", "#title + '-' + #code"));
        schema.add(FlowNodeField.input("saveAs", "写入属性名", true));
        return schema;
    }

    /**
    * 执行脚本节点：计算表达式并写入上下文属性。
    *
    * @param context 当前流程上下文
    */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String expression = props.getString("expression");
        String saveAs = props.getString("saveAs");
        if (expression == null || expression.isEmpty()) {
            /**
            * IllegalArgumentException。
            * @param 参数" 参数"
            * @return IllegalArgumentException的结果
            */
            throw new IllegalArgumentException("spider.script 节点缺少 expression 参数");
        }
        if (saveAs == null || saveAs.isEmpty()) {
            /**
            * IllegalArgumentException。
            * @param 参数" 参数"
            * @return IllegalArgumentException的结果
            */
            throw new IllegalArgumentException("spider.script 节点缺少 saveAs 参数");
        }
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.addPropertyAccessor(new MapAccessor());
        evalContext.setVariable(VAR_DATA, context.getData());
        for (Map.Entry<String, Object> entry : context.getAttributes().entrySet()) {
            evalContext.setVariable(entry.getKey(), entry.getValue());
        }
        Expression parsed = expressionParser.parseExpression(expression);
        Object result = parsed.getValue(evalContext);
        context.setAttribute(saveAs, result);
        log.info("spider.script 计算完成: saveAs={} result={}", saveAs, result);
    }
}
