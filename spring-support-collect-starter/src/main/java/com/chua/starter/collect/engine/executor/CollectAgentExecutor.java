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
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.vo.CollectOptionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * 系统提示词参数：非空时拼在用户提示词前。
     */
    public static final String SYSTEM_PROMPT = "systemPrompt";

    /**
     * 输入变量参数：每行「名=值」或「名: 值」，值支持 ${}。
     */
    public static final String INPUT_VARIABLES = "inputVariables";

    /**
     * 输出解析格式参数：text / json / regex。
     */
    public static final String OUTPUT_FORMAT = "outputFormat";

    /**
     * 正则提取表达式参数（输出解析为 regex 时使用）。
     */
    public static final String OUTPUT_PATTERN = "outputPattern";

    /**
     * 失败策略参数：fail / skip / defaultValue。
     */
    public static final String FAILURE_STRATEGY = "failureStrategy";

    /**
     * 失败兜底值参数（失败策略为 defaultValue 时写入输出变量）。
     */
    public static final String FAILURE_VALUE = "failureValue";

    /**
     * Agent 调用超时毫秒参数；留空用平台兜底配置，0 表示不限制。
     */
    public static final String CALL_TIMEOUT = "callTimeoutMs";

    /**
     * 输出解析：原文。
     */
    public static final String FORMAT_TEXT = "text";

    /**
     * 输出解析：JSON（解析成映射/列表/标量后写入变量）。
     */
    public static final String FORMAT_JSON = "json";

    /**
     * 输出解析：正则提取。
     */
    public static final String FORMAT_REGEX = "regex";

    /**
     * 失败策略：中断流程（默认，向上抛错）。
     */
    public static final String STRATEGY_FAIL = "fail";

    /**
     * 失败策略：记警告后跳过，流程继续。
     */
    public static final String STRATEGY_SKIP = "skip";

    /**
     * 失败策略：写入兜底值后继续。
     */
    public static final String STRATEGY_DEFAULT = "defaultValue";

    private final CollectAgentCatalog agentCatalog;

    /**
     * 平台配置，提供 Agent 节点未显式配置时的兜底默认值。
     */
    private final CollectProperties properties;

    /**
     * Agent 调用线程池。
     *
     * <p>{@code Agent#run} 是阻塞调用，超时控制需要「另开线程执行、当前线程限时等待」；
     * 用独立池避免占用公共池。守护线程不阻止宿主退出。</p>
     */
    private final ExecutorService agentPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "collect-agent-call");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 构造 Agent 节点。
     *
     * @param agentCatalog Agent 目录
     * @param properties   平台配置
     */
    public CollectAgentExecutor(CollectAgentCatalog agentCatalog, CollectProperties properties) {
        this.agentCatalog = agentCatalog;
        this.properties = properties;
    }

    /**
     * 释放 Agent 调用线程池。
     */
    @PreDestroy
    public void close() {
        agentPool.shutdownNow();
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
        CollectParamMeta systemPrompt = CollectShapeMetas.text(SYSTEM_PROMPT, "系统提示词", "textarea",
                "可选；非空时拼在用户提示词前，作为本轮调用的角色与规则约束");
        CollectParamMeta inputVariables = CollectShapeMetas.text(INPUT_VARIABLES, "输入变量", "textarea",
                "可选；每行「名=值」或「名: 值」，值支持 ${}，渲染成变量清单附在提示词后");
        CollectParamMeta outputFormat = CollectShapeMetas.option(OUTPUT_FORMAT, "输出解析", FORMAT_TEXT,
                "text 原文 / json 解析成对象或数组 / regex 按正则提取", FORMAT_TEXT, FORMAT_JSON, FORMAT_REGEX);
        CollectParamMeta outputPattern = CollectShapeMetas.text(OUTPUT_PATTERN, "正则表达式", "text",
                "输出解析为 regex 时必填；取第 1 个分组，无分组取整段匹配");
        CollectParamMeta failureStrategy = CollectShapeMetas.option(FAILURE_STRATEGY, "失败策略",
                properties.getAgent().getFailureStrategy(),
                "fail 中断流程 / skip 记警告后跳过 / defaultValue 写入兜底值继续",
                STRATEGY_FAIL, STRATEGY_SKIP, STRATEGY_DEFAULT);
        CollectParamMeta failureValue = CollectShapeMetas.text(FAILURE_VALUE, "失败兜底值", "text",
                "失败策略为 defaultValue 时写入输出变量的值，支持 ${}；留空写空串");
        CollectParamMeta callTimeout = CollectShapeMetas.text(CALL_TIMEOUT, "调用超时毫秒", "number",
                "留空用 plugin.collect.agent.timeout-ms 兜底（默认 120000），0 表示不限制");
        CollectShapeMeta meta = CollectShapeMetas.of(SHAPE, "Agent", "AI", "magic-stick",
                "调用平台已注册的 AI Agent，把输出写入采集变量");
        return CollectShapeMetas.with(meta, agent, prompt, output, systemPrompt, inputVariables,
                outputFormat, outputPattern, failureStrategy, failureValue, callTimeout);
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
        String output = node.param(OUTPUT).isBlank() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        String strategy = resolveFailureStrategy(node);
        try {
            String input = buildInput(node, prompt, variables);
            AgentResponse response = invoke(agent, agentName, input, resolveCallTimeoutMs(node));
            if (response == null) {
                throw new IllegalStateException("Agent 未返回结果: " + agentName);
            }
            String text = response.getOutput();
            if (text == null) {
                throw new IllegalStateException("Agent 返回结果为空: " + agentName);
            }
            variables.put(output, parseOutput(node, text, agentName));
        } catch (Exception ex) {
            if (STRATEGY_SKIP.equalsIgnoreCase(strategy)) {
                log.warn("[collect] Agent 节点按失败策略跳过: agent={}, output={}, 原因: {}",
                        agentName, output, ex.getMessage());
                return;
            }
            if (STRATEGY_DEFAULT.equalsIgnoreCase(strategy)) {
                log.warn("[collect] Agent 节点按失败策略写兜底值: agent={}, output={}, 原因: {}",
                        agentName, output, ex.getMessage());
                variables.put(output, CollectExpression.evalText(node.param(FAILURE_VALUE), variables));
                return;
            }
            throw ex;
        }
    }

    /**
     * 组装 Agent 输入：系统提示词前置 + 用户提示词 + 输入变量清单后置。
     *
     * <p>运行期 {@code Agent} 契约只收一段输入文本、没有逐次 system prompt 入参，
     * 因此系统提示词以输入首段注入；改注册表里的共享 Agent 实例会影响其他调用方，不可取。</p>
     *
     * @param node      节点
     * @param prompt    已求值的用户提示词
     * @param variables 流程变量
     * @return 组装后的输入文本
     */
    private String buildInput(CollectNode node, String prompt, Map<String, Object> variables) {
        String system = CollectExpression.evalText(node.param(SYSTEM_PROMPT), variables);
        StringBuilder input = new StringBuilder();
        if (system != null && !system.isBlank()) {
            input.append(system.trim()).append("\n\n");
        }
        input.append(prompt);
        String rendered = renderInputVariables(node.param(INPUT_VARIABLES), variables);
        if (!rendered.isEmpty()) {
            input.append("\n\n【输入变量】\n").append(rendered);
        }
        return input.toString();
    }

    /**
     * 渲染输入变量清单：每行「名=值」或「名: 值」，值支持 ${} 求值。
     *
     * @param raw       输入变量原文
     * @param variables 流程变量
     * @return 「名: 值」行清单；无有效行返回空串
     */
    private String renderInputVariables(String raw, Map<String, Object> variables) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder rendered = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            int equals = text.indexOf('=');
            int colon = text.indexOf(':');
            // 两个分隔符都存在时取先出现的那个，避免「http://」这类值被冒号截断
            int separator = equals >= 0 && (colon < 0 || equals < colon) ? equals : colon;
            if (separator <= 0) {
                continue;
            }
            String name = text.substring(0, separator).trim();
            if (name.isEmpty()) {
                continue;
            }
            String value = CollectExpression.evalText(text.substring(separator + 1).trim(), variables);
            if (rendered.length() > 0) {
                rendered.append('\n');
            }
            rendered.append(name).append(": ").append(value);
        }
        return rendered.toString();
    }

    /**
     * 调用 Agent，按调用级超时限时等待。
     *
     * <p>超时后取消等待线程并抛错（由失败策略决定后续）；
     * 底层 Agent 若不响应中断，线程会跑完当次调用后自然结束。</p>
     *
     * @param agent     运行期 Agent
     * @param agentName Agent 名称（报错定位）
     * @param input     组装后的输入
     * @param timeoutMs 调用超时毫秒，0 表示不限制
     * @return Agent 响应
     * @throws Exception 调用失败或超时
     */
    private AgentResponse invoke(Agent agent, String agentName, String input, long timeoutMs) throws Exception {
        if (timeoutMs <= 0L) {
            try {
                return agent.run(input);
            } catch (Exception ex) {
                throw new IllegalStateException("Agent 调用失败: " + agentName + "，原因: " + ex.getMessage(), ex);
            }
        }
        CompletableFuture<AgentResponse> future = CompletableFuture.supplyAsync(() -> agent.run(input), agentPool);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new IllegalStateException("Agent 调用超时: " + agentName + "，上限 " + timeoutMs + "ms");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException("Agent 调用失败: " + agentName + "，原因: " + cause.getMessage(), cause);
        } catch (InterruptedException ex) {
            future.cancel(true);
            throw ex;
        }
    }

    /**
     * 按输出解析格式把 Agent 原文转成写入变量的值。
     *
     * @param node      节点
     * @param text      Agent 输出原文
     * @param agentName Agent 名称（报错定位）
     * @return 解析后的值
     */
    private Object parseOutput(CollectNode node, String text, String agentName) {
        String format = node.param(OUTPUT_FORMAT);
        if (format == null || format.isBlank() || FORMAT_TEXT.equalsIgnoreCase(format.trim())) {
            return text;
        }
        if (FORMAT_JSON.equalsIgnoreCase(format.trim())) {
            Object parsed = CollectJson.parseAny(text);
            if (parsed == null) {
                throw new IllegalStateException("Agent 输出不是合法 JSON: " + agentName);
            }
            return parsed;
        }
        if (FORMAT_REGEX.equalsIgnoreCase(format.trim())) {
            String pattern = node.param(OUTPUT_PATTERN);
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalStateException("输出解析选择了 regex 但未填写正则表达式");
            }
            Matcher matcher = Pattern.compile(pattern.trim()).matcher(text);
            if (!matcher.find()) {
                throw new IllegalStateException("Agent 输出未匹配正则: " + agentName + "，pattern=" + pattern);
            }
            return matcher.groupCount() >= 1 && matcher.group(1) != null ? matcher.group(1) : matcher.group();
        }
        // 未知格式按原文，不做静默失败
        return text;
    }

    /**
     * 失败策略：节点参数优先，留空用平台兜底配置。
     *
     * @param node 节点
     * @return 失败策略
     */
    private String resolveFailureStrategy(CollectNode node) {
        String raw = node.param(FAILURE_STRATEGY);
        return raw == null || raw.isBlank() ? properties.getAgent().getFailureStrategy() : raw.trim();
    }

    /**
     * 调用超时毫秒：节点参数优先；留空或非法值用平台兜底配置，0 表示不限制。
     *
     * @param node 节点
     * @return 超时毫秒
     */
    private long resolveCallTimeoutMs(CollectNode node) {
        String raw = node.param(CALL_TIMEOUT);
        if (raw == null || raw.isBlank()) {
            return properties.getAgent().getTimeoutMs();
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return properties.getAgent().getTimeoutMs();
        }
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
