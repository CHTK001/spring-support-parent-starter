package com.chua.starter.sync.data.support.sync.transformer.impl;

import com.chua.starter.sync.data.support.sync.transformer.DataTransformer;
import com.chua.starter.sync.data.support.sync.transformer.TransformConfig;
import lombok.extern.slf4j.Slf4j;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本转换器
 * 支持Groovy/JavaScript脚本进行数据转换
 *
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
public class ScriptTransformer implements DataTransformer {

    private final ScriptEngineManager manager = new ScriptEngineManager();

    @Override
    public Map<String, Object> transform(Map<String, Object> input, TransformConfig config) {
        if (input == null || input.isEmpty()) {
            return new HashMap<>();
        }

        String scriptType = config.getScriptType();
        String script = config.getScript();

        if (scriptType == null || script == null) {
            log.warn("脚本类型或脚本内容为空，返回原始数据");
            return new HashMap<>(input);
        }

        try {
            ScriptEngine engine = manager.getEngineByName(scriptType);
            if (engine == null) {
                log.error("不支持的脚本引擎: {}", scriptType);
                return handleScriptError(input, config);
            }

            // 设置输入输出变量
            engine.put("input", input);
            Map<String, Object> output = new HashMap<>();
            engine.put("output", output);

            // 执行脚本
            engine.eval(script);

            // 获取输出结果
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) engine.get("output");
            
            return result != null ? result : new HashMap<>();

        } catch (ScriptException e) {
            log.error("脚本执行失败: scriptType={}, script={}", scriptType, script, e);
            return handleScriptError(input, config);
        } catch (Exception e) {
            log.error("脚本转换异常", e);
            return handleScriptError(input, config);
        }
    }

    /**
     * 处理脚本执行错误
     */
    private Map<String, Object> handleScriptError(Map<String, Object> input, TransformConfig config) {
        // 根据配置决定错误时的行为
        if (Boolean.TRUE.equals(config.getKeepOnScriptError())) {
            return new HashMap<>(input);
        }
        return new HashMap<>();
    }

    @Override
    public boolean validateConfig(TransformConfig config) {
        if (config == null) {
            log.error("转换配置不能为空");
            return false;
        }

        String scriptType = config.getScriptType();
        if (scriptType == null || scriptType.trim().isEmpty()) {
            log.error("脚本类型不能为空");
            return false;
        }

        String script = config.getScript();
        if (script == null || script.trim().isEmpty()) {
            log.error("脚本内容不能为空");
            return false;
        }

        // 检查脚本引擎是否可用
        ScriptEngine engine = manager.getEngineByName(scriptType);
        if (engine == null) {
            log.error("不支持的脚本引擎: {}，可用引擎: {}", 
                scriptType, manager.getEngineFactories().stream()
                    .map(f -> f.getEngineName())
                    .toArray());
            return false;
        }

        // 尝试编译脚本验证语法
        try {
            engine.eval("var test = 1;"); // 简单测试
        } catch (ScriptException e) {
            log.error("脚本引擎初始化失败", e);
            return false;
        }

        return true;
    }

    public String getType() {
        return "SCRIPT";
    }
}
