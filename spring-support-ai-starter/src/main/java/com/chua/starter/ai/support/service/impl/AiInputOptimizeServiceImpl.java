package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.model.InputOptimizeRequest;
import com.chua.starter.ai.support.model.InputOptimizeResponse;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AiInputOptimizeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
* AI 输入优化服务实现。
*
* <p>读取 {@code input} 分组全局设置（sys_ai_global_setting）：
* <ul>
*   <li>optimizer_model：优化模型，为空时回退到 {@code runtime} 分组默认对话模型；</li>
*   <li>optimizer_prompt：自定义优化提示词，为空时使用内置默认提示词；</li>
*   <li>optimizer_temperature：优化温度；</li>
*   <li>optimizer_enabled：优化开关。</li>
* </ul>
* 通过 {@link ChatClientRegistry} 获取 对话客户端 后，以系统提示词 + 用户输入的方式执行一次同步对话，
* 返回改写后的文本。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInputOptimizeServiceImpl implements AiInputOptimizeService {

    /**
    * 输入优化配置分组
    */
    private static final String GROUP = "input";

    /**
    * runtime 分组（全局对话路由配置）
    */
    private static final String RUNTIME_GROUP = "runtime";

    /**
    * 默认优化温度
    */
    private static final double DEFAULT_OPTIMIZE_TEMPERATURE = 0.3;

    /**
    * 内置默认优化提示词
    */
    private static final String DEFAULT_OPTIMIZE_PROMPT = """
            你是一个输入优化助手。
            在不改变用户真实意图的前提下，将用户输入改写为更清晰、更准确、更适合模型理解的版本。
            不要解释，不要添加无关内容，只输出优化后的文本。

            输入：
            %s
            """;

    /**
    * 全局设置读取器
    */
    private final GlobalSettingReader globalSettingReader;

    /**
    * 全局 对话客户端 注册中心
    */
    private final ChatClientRegistry chatClientRegistry;

    /**
    * 执行输入优化。
    *
    * @param request 优化请求
    * @return 优化结果
    */
    @Override
    public InputOptimizeResponse optimize(InputOptimizeRequest request) {
        String input = StringUtils.defaultString(request.getInput(), "");
        if (StringUtils.isBlank(input)) {
            return InputOptimizeResponse.of(input, input, "", 0L);
        }

        // 是否启用输入优化
        boolean enabled = globalSettingReader.getBool(GROUP, "optimizer_enabled", true);
        if (!enabled) {
            return InputOptimizeResponse.of(input, input, "", 0L);
        }

        long start = System.currentTimeMillis();
        try {
            // 解析优化模型：请求 > 全局配置 > runtime 默认模型
            String model = request.getModel();
            if (StringUtils.isBlank(model)) {
                model = globalSettingReader.getStr(GROUP, "optimizer_model");
            }
            ChatClient client = resolveChatClient(model);
            if (client == null) {
                return InputOptimizeResponse.of(input, input, model, System.currentTimeMillis() - start);
            }

            // 解析提示词：请求 > 全局配置 > 内置默认
            String systemPrompt = request.getSystemPrompt();
            if (StringUtils.isBlank(systemPrompt)) {
                systemPrompt = globalSettingReader.getStr(GROUP, "optimizer_prompt");
            }
            if (StringUtils.isBlank(systemPrompt)) {
                systemPrompt = DEFAULT_OPTIMIZE_PROMPT;
            }
            String prompt = systemPrompt.contains("%s")
                    ? String.format(systemPrompt, input)
                    : systemPrompt;

            // 解析温度
            double temperature = globalSettingReader.getDouble(GROUP, "optimizer_temperature",
                    DEFAULT_OPTIMIZE_TEMPERATURE);

            String optimized = client
                    .model(model)
                    .system(systemPrompt)
                    .temperature(temperature)
                    .chatSync(prompt);

            if (StringUtils.isBlank(optimized)) {
                return InputOptimizeResponse.of(input, input, model, System.currentTimeMillis() - start);
            }
            return InputOptimizeResponse.of(input, optimized, model, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("[InputOptimize] 输入优化失败，返回原始输入: {}", e.getMessage());
            return InputOptimizeResponse.of(input, input, "", System.currentTimeMillis() - start);
        }
    }

    /**
    * 解析优化用 对话客户端。
    *
    * <p>指定模型时优先按模型查找注册实例；否则使用全局聚合 ChatClient。
    * 全局配置未配置模型时，从 {@code runtime} 分组读取默认模型名。</p>
    *
    * @param model 配置的模型名（可能为空）
    * @return ChatClient 实例；不可用时返回 空
    */
    private ChatClient resolveChatClient(String model) {
        String effectiveModel = model;
        if (StringUtils.isBlank(effectiveModel)) {
            effectiveModel = globalSettingReader.getStr(RUNTIME_GROUP, "default_model");
        }
        if (StringUtils.isNotBlank(effectiveModel)) {
            ChatClient byModel = chatClientRegistry.getByModel(effectiveModel);
            if (byModel != null) {
                return byModel;
            }
        }
        return chatClientRegistry.getDefault();
    }
}
