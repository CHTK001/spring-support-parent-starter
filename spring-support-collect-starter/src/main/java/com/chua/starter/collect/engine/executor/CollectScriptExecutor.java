// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectGroovyRuntime;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脚本节点：用 Groovy 处理上游变量并产出新变量。
 *
 * <p>脚本里的绑定：每个上游变量按下划线作用域直接引用，{@code vars} 是可写的同一份变量表，
 * {@code fn} 指向内置函数库（{@code fn.md5(x)}、{@code fn.dateNow('yyyyMMdd')} 等），
 * 另有 {@code flowId}/{@code taskId}/{@code taskNo}/{@code nodeId} 可读。</p>
 *
 * <p>出参三选一：返回 Map 时按作用域整份并入变量表；返回标量时写入"输出变量名"；
 * 也可直接往 {@code vars} 里写。脚本最后一条表达式即返回值，不需要写 return。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "plugin.collect.script", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectScriptExecutor implements CollectShapeExecutor {

    /**
     * 脚本正文参数名。
     */
    public static final String SCRIPT = "script";

    /**
     * 标量输出变量名参数名。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 标量输出默认变量名。
     */
    public static final String DEFAULT_OUTPUT = "scriptResult";

    /**
     * 作用域参数名。
     */
    public static final String SCOPE = "scope";

    /**
     * 全局作用域取值。
     */
    public static final String SCOPE_GLOBAL = "global";

    /**
     * 超时毫秒参数名。
     */
    public static final String TIMEOUT = "timeout";

    /**
     * 默认脚本超时毫秒。
     */
    private static final long DEFAULT_TIMEOUT = 30000L;

    @Override
    public String supportShape() {
        return "script";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "脚本", "控制", "code",
                "执行 Groovy 脚本加工变量，返回 Map 即批量出参");
        // 已下线：安全与维护考虑，脚本节点不再进入调色板，存量流程仍可读但建议迁移。
        meta.setHidden(true);
        CollectParamMeta script = CollectShapeMetas.required(SCRIPT, "脚本正文", "textarea",
                "上游变量按名字直接引用，vars 可读写同一份变量表，fn 调内置函数；最后一条表达式即返回值");
        CollectParamMeta output = CollectShapeMetas.text(OUTPUT, "输出名", "text",
                "脚本返回标量时写入该变量，返回 Map 时忽略");
        output.setDefaultValue(DEFAULT_OUTPUT);
        output.setRequired(true);
        return CollectShapeMetas.with(meta, script, output,
                CollectParamMeta.select(SCOPE, "作用域", "branch", SCOPE_GLOBAL),
                CollectShapeMetas.text(TIMEOUT, "超时毫秒", "number", "留空按 30000"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        String raw = node.param(SCRIPT);
        if (raw.isBlank()) {
            throw new IllegalStateException("脚本节点没有填写脚本正文");
        }
        boolean global = SCOPE_GLOBAL.equalsIgnoreCase(node.param(SCOPE));
        String output = node.param(OUTPUT).trim();
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.putAll(variables);
        bindings.put("vars", variables);
        bindings.put("fn", CollectGroovyRuntime.functionObject());
        bindings.put("flowId", context.getFlowId());
        bindings.put("taskId", context.getTaskId());
        bindings.put("taskNo", context.getTaskNo());
        bindings.put("nodeId", node.id());
        Object value = CollectGroovyRuntime.evaluate(raw, bindings, node.longParam(TIMEOUT, DEFAULT_TIMEOUT));
        // 审计：脚本节点等价于服务端任意代码执行，必须留下可追溯的一条（谁在哪个流程/任务里跑了脚本）。
        // 类型开关是部署侧的 plugin.collect.script.enable=false；这里只做留痕，不改变行为。
        log.warn("[采集审计] 脚本节点执行 flowId={} taskNo={} node={} 脚本首行: {}",
                context.getFlowId(), context.getTaskNo(), node.name(), firstLine(raw));
        writeBack(variables, context, global, output.isBlank() ? DEFAULT_OUTPUT : output, value);
    }

    /**
     * 取脚本首行作为审计摘要。
     *
     * @param script 脚本正文
     * @return 最多 100 字的单行摘要
     */
    private String firstLine(String script) {
        String first = script == null ? "" : script.lines().findFirst().orElse("").trim();
        return first.length() > 100 ? first.substring(0, 100) + "…" : first;
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        String first = node.param(SCRIPT).lines().findFirst().orElse("").trim();
        if (first.length() > 80) {
            first = first.substring(0, 80) + "…";
        }
        return first.isEmpty() ? null : first;
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return DEFAULT_OUTPUT;
    }

    /**
     * 按返回类型把脚本结果并入变量表。
     *
     * @param variables 当前作用域
     * @param context   执行上下文
     * @param global    是否写全局变量
     * @param output    标量输出变量名
     * @param value     脚本返回值
     */
    private void writeBack(Map<String, Object> variables, CollectContext context,
                           boolean global, String output, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            put(variables, context, global, output, value);
            return;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            put(variables, context, global, String.valueOf(entry.getKey()), entry.getValue());
        }
    }

    /**
     * 写单个变量，全局作用域走上下文以跨分支共享。
     *
     * @param variables 当前作用域
     * @param context   执行上下文
     * @param global    是否全局
     * @param name      变量名
     * @param value     变量值
     */
    private void put(Map<String, Object> variables, CollectContext context, boolean global,
                     String name, Object value) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (global) {
            context.putRootVariable(name, value);
        } else {
            variables.put(name.trim(), value == null ? "" : value);
        }
    }
}