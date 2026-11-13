package com.chua.starter.collect.engine.executor;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.vo.CollectOptionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Agent 节点：把提示词交给平台已注册的运行期 Agent，执行结果写入出参变量。
 *
 * <p>Agent 目录来自 {@link CollectAgentCatalog}，不在采集模块写死名称；没有
 * AI starter 或注册表为空时，节点仍会出现在设计器中，但下拉为空，执行时给出明确原因。
 * 调用通过公共 {@link Agent} 契约完成，不依赖具体 Agent 实现。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@ConditionalOnClass(name = "com.chua.starter.ai.support.registry.AgentRegistry")
@ConditionalOnProperty(prefix = "plugin.collect.agent", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectAgentExecutor implements CollectShapeExecutor {

    /**
     * 节点类型标识。
     */
    public static final String SHAPE = "agent";

    /**
     * Agent 名称参数。
     */
    public static final String AGENT_NAME = "agentName";

    /**
     * 用户提示词参数。
     */
    public static final String PROMPT = "prompt";

    /**
     * 输出变量参数。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 默认输出变量名。
     */
    public static final String DEFAULT_OUTPUT = "agentResult";

    private final CollectAgentCatalog agentCatalog;

    /**
     * 构造 Agent 节点。
     *
     * @param agentCatalog Agent 目录
     */
    public CollectAgentExecutor(CollectAgentCatalog agentCatalog) {
        this.agentCatalog = agentCatalog;
    }

    @Override
    public String supportShape() {
        return SHAPE;
    }

    @Override
    public CollectShapeMeta meta() {
        List<String> names = agentCatalog.options().stream().map(CollectOptionVO::value).toList();
        CollectParamMeta agent = CollectParamMeta.select(AGENT_NAME, "Agent", names.toArray(String[]::new));
        agent.setRequired(true);
        agent.setTip(names.isEmpty()
                ? "当前没有已注册的 Agent；请先在 AI Agent 管理中创建并确保运行期注册成功"
                : "来自运行期 AgentRegistry；新增或重载 Agent 后刷新设计器即可更新");
        CollectParamMeta prompt = CollectShapeMetas.required(PROMPT, "提示词", "textarea",
                "支持 ${} 引用上游变量；作为 Agent 的输入");
        CollectParamMeta output = CollectShapeMetas.text(OUTPUT, "输出名", "text",
                "Agent 输出写入的变量名，留空用 agentResult");
        output.setDefaultValue(DEFAULT_OUTPUT);
        output.setRequired(true);
        CollectShapeMeta meta = CollectShapeMetas.of(SHAPE, "Agent", "AI", "magic-stick",
                "调用平台已注册的 AI Agent，把输出写入采集变量");
        return CollectShapeMetas.with(meta, agent, prompt, output);
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        String agentName = CollectExpression.evalText(node.param(AGENT_NAME), variables).trim();
        if (agentName.isEmpty()) {
            throw new IllegalStateException("Agent 节点没有选择 Agent");
        }
        String prompt = CollectExpression.evalText(node.param(PROMPT), variables);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalStateException("Agent 节点没有填写提示词");
        }
        Agent agent = agentCatalog.get(agentName);
        if (agent == null) {
            throw new IllegalStateException("Agent 未注册或当前运行期不可用: " + agentName);
        }

        AgentResponse response;
        try {
            response = agent.run(prompt);
        } catch (Exception ex) {
            throw new IllegalStateException("Agent 调用失败: " + agentName + "，原因: " + ex.getMessage(), ex);
        }
        if (response == null) {
            throw new IllegalStateException("Agent 未返回结果: " + agentName);
        }
        String text = response.getOutput();
        if (text == null) {
            throw new IllegalStateException("Agent 返回结果为空: " + agentName);
        }
        String output = node.param(OUTPUT).isBlank() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        variables.put(output, text);
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        String output = node.param(OUTPUT).isBlank() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        Object value = variables.get(output);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        // [P3C A20 豁免]
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return node.param(OUTPUT).isBlank() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
    }
}
