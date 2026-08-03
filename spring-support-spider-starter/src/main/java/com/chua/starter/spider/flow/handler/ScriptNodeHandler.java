package com.chua.starter.spider.flow.handler;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.flow.NodeHandler;
import com.chua.starter.spider.flow.SpiderFlowContext;
import com.chua.starter.spider.flow.SpiderFlowUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本节点处理器：在 Nashorn / 内置 Groovy 中执行小段脚本。
 *
 * <p>config JSON 结构：</p>
 * <pre>
 * {
 *   "language": "js",         // js | groovy（默认 js，依赖 JDK Nashorn）
 *   "script":   "vars.put('foo', 1 + 1);",
 *   "saveAs":   "result"      // 可选：把 vars 里的某个 key 提取到 ctx
 * }
 * </pre>
 *
 * <p>脚本可访问 binding：{@code vars = ctx.vars}（可读写），
 * 以及 {@code ctx} / {@code log} / {@code node} 等辅助对象。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ScriptNodeHandler implements NodeHandler {

    /**
     * 节点类型编码
     */
    private static final String TYPE = "script";

    /**
     * 默认脚本语言
     */
    private static final String DEFAULT_LANGUAGE = "js";

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public String execute(SpiderFlowNode node, SpiderFlowContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getFlowNodeConfig());
        String language = SpiderFlowUtils.defaultIfEmpty(asString(config.get("language")), DEFAULT_LANGUAGE);
        String script = asString(config.get("script"));
        String saveAs = asString(config.get("saveAs"));

        if (StringUtils.isBlank(script)) {
            log.warn("[Script] 节点 {} 脚本为空，跳过", node.getFlowNodeKey());
            return "empty script";
        }

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName(language);
        if (engine == null) {
            throw new ScriptException("不支持的脚本语言: " + language);
        }

        engine.put("vars", context.getVars());
        engine.put("ctx", context);
        engine.put("log", log);
        engine.put("node", node);
        Object result = engine.eval(script);
        if (saveAs != null) {
            context.putVar(saveAs, result);
        }
        log.info("[Flow {}] Script[{}] executed, result={}", context.getExecutionNo(), language, result);
        return "script ok";
    }

    /**
     * 解析 config JSON。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[Script] config JSON 解析失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Object 转 String。
     */
    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}


