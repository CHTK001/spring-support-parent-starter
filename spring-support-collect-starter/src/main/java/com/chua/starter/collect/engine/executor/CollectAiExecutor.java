// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * AI 节点：调用同机 ai-starter 的同步对话门面，把模型回答写入出参变量。
 *
 * <p>本执行器不直接依赖 ai-starter 的编译期类；运行时通过反射调用
 * {@code AiChatFacadeService.callForResult(AiChatRequest)}。
 * 只有 classpath 上实际集成了 ai-starter 时 Spring 才会装配本节点，
 * 设计器工具箱里也只会在这时出现「AI」节点。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@ConditionalOnClass(name = "com.chua.starter.ai.support.service.AiChatFacadeService")
@ConditionalOnProperty(prefix = "plugin.collect.ai", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectAiExecutor implements CollectShapeExecutor {

    /**
     * 节点类型标识。
     */
    public static final String SHAPE = "ai";

    /**
     * 用户提示词参数名。
     */
    public static final String PROMPT = "prompt";

    /**
     * AI 模型参数名。
     */
    public static final String MODEL = "model";

    /**
     * 系统提示词参数名。
     */
    public static final String SYSTEM_PROMPT = "systemPrompt";

    /**
     * 输出变量参数名。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 默认输出变量名。
     */
    public static final String DEFAULT_OUTPUT = "aiResult";

    private final ObjectProvider<Object> aiServiceProvider;

    public CollectAiExecutor(ObjectProvider<Object> aiServiceProvider) {
        this.aiServiceProvider = aiServiceProvider;
    }

    @Override
    public String supportShape() {
        return SHAPE;
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(SHAPE, "AI", "AI", "magic-stick",
                "把一句 prompt 交给同机 ai-starter，模型回答写入出参变量；仅集成 ai-starter 时可用");
        CollectParamMeta prompt = CollectShapeMetas.required(PROMPT, "提示词", "textarea",
                "支持 ${} 引用上游变量；模型按这段要求生成文本");
        CollectParamMeta model = CollectShapeMetas.text(MODEL, "模型", "text",
                "留空使用 ai-starter 默认模型，填写具体模型名时按该模型调用");
        CollectParamMeta system = CollectShapeMetas.text(SYSTEM_PROMPT, "系统提示词", "textarea",
                "可选；给模型定义角色、输出格式或约束");
        CollectParamMeta output = CollectShapeMetas.text(OUTPUT, "输出名", "text",
                "模型回答写入的变量名，留空用 aiResult");
        output.setDefaultValue(DEFAULT_OUTPUT);
        output.setRequired(true);
        return CollectShapeMetas.with(meta, prompt, model, system, output);
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        String prompt = CollectExpression.evalText(node.param(PROMPT), variables);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalStateException("AI 节点没有填写提示词");
        }
        Object service = aiServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("AI 节点需要集成 ai-starter，当前运行部署未加载 AI 服务");
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> requestType = Class.forName("com.chua.starter.ai.support.model.AiChatRequest", true, loader);
        Object request = requestType.getDeclaredConstructor().newInstance();
        setString(request, "setPrompt", prompt);
        setString(request, "setModel", CollectExpression.evalText(node.param(MODEL), variables));
        setString(request, "setSystemPrompt", CollectExpression.evalText(node.param(SYSTEM_PROMPT), variables));
        setString(request, "setCallType", "collect_ai_node");
        setString(request, "setUsageSource", "service");

        Method call = service.getClass().getMethod("callForResult", requestType);
        Object result = call.invoke(service, request);
        if (result == null) {
            throw new IllegalStateException("AI 服务未返回结果");
        }
        String text = (String) result.getClass().getMethod("text").invoke(result);
        String error = (String) result.getClass().getMethod("errorMessage").invoke(result);
        if (error != null && !error.isBlank()) {
            throw new IllegalStateException("AI 调用失败: " + error);
        }

        String output = node.param(OUTPUT).isBlank() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        variables.put(output, text == null ? "" : text);
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        String output = node.param(OUTPUT).isBlank() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        Object value = variables.get(output);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return node.param(OUTPUT).isBlank() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
    }

    private void setString(Object target, String methodName, String value) throws Exception {
        if (value == null || value.isBlank()) {
            return;
        }
        target.getClass().getMethod(methodName, String.class).invoke(target, value);
    }}