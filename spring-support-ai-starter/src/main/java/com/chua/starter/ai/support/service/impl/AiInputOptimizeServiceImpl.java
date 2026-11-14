package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.chat.ModelClientResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.model.InputOptimizeRequest;
import com.chua.starter.ai.support.model.InputOptimizeResponse;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AiInputOptimizeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

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
     * 固定 system 指令（组合模板场景使用）
     */
    private static final String DEFAULT_SYSTEM_INSTRUCTION = """
            你是一个输入优化助手。请在不改变用户真实意图的前提下，将用户输入改写为更清晰、更准确、更适合模型理解的版本。
            不要解释，不要添加无关内容，只输出优化后的文本。
            """;

    /**
     * 内置默认优化提示词（通用场景）
     */
    private static final String DEFAULT_OPTIMIZE_PROMPT = """
            你是一个输入优化助手。
            在不改变用户真实意图的前提下，将用户输入改写为更清晰、更准确、更适合模型理解的版本。
            不要解释，不要添加无关内容，只输出优化后的文本。

            输入：
            %s
            """;

    /**
     * 内置默认优化提示词（图像生成场景）
     *
     * <p>与通用版的区别：要求输出「画面要素」而不是通顺的句子。
     * 通用指令会把「一只猫」原样留着（模型认为已经很清晰），
     * 或改写成散文，画面里该有的主体、构图、光线、材质、镜头全丢了。</p>
     */
    private static final String DEFAULT_IMAGE_PROMPT = """
            你是图像生成提示词专家。把用户的输入扩写成一条高质量的中文画面提示词。

            要求：
            1. 补齐画面要素：主体、动作与神态、构图与视角、光线、色彩、材质质感、背景与氛围。
            2. 保留用户已给出的信息，不要替换主体、不要改变意图，不要凭空加入文字、水印、logo。
            3. 保持用户使用的语言。
            4. 直接输出提示词本身，单行、用逗号分隔，不要序号、不要标题、
               不要「优化后的提示词：」之类的前缀，不要用引号或代码块包裹，不要解释。

            输入：
            %s
            """;

    /**
     * 内置默认优化提示词（视频生成场景）
     *
     * <p>在图像要素之上补时间维度：动作过程、镜头运动、时长节奏。</p>
     */
    private static final String DEFAULT_VIDEO_PROMPT = """
            你是视频生成提示词专家。把用户的输入扩写成一条高质量的中文视频提示词。

            要求：
            1. 补齐要素：主体、动作过程、场景、光线、色彩、氛围、镜头运动（推拉摇移或固定）、节奏。
            2. 保留用户已给出的信息，不要替换主体、不要改变意图，不要凭空加入文字、水印、字幕。
            3. 保持用户使用的语言。
            4. 直接输出提示词本身，单行、用逗号分隔，不要序号、不要标题、
               不要「优化后的提示词：」之类的前缀，不要用引号或代码块包裹，不要解释。

            输入：
            %s
            """;

    /**
     * 场景 → 内置默认提示词
     */
    private static final Map<String, String> SCENE_PROMPTS = Map.of(
            "image", DEFAULT_IMAGE_PROMPT,
            "video", DEFAULT_VIDEO_PROMPT);

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 全局 对话客户端 注册中心
     */
    private final ChatClientRegistry chatClientRegistry;
    private final ModelClientResolver modelClientResolver;
    private final com.chua.starter.ai.support.service.SysAiUsageService usageService;

    /**
     * 执行输入优化。
     *
     * @param request 优化请求
     * @return 优化结果
     */
    @Override
    public InputOptimizeResponse optimize(InputOptimizeRequest request) {
        String input = StringUtils.defaultString(request.input(), "");
        if (StringUtils.isBlank(input)) {
            return InputOptimizeResponse.of(input, input, "", 0L, InputOptimizeResponse.STATUS_BLANK);
        }

        // 是否启用输入优化
        boolean enabled = globalSettingReader.getBool(GROUP, "optimizer_enabled", true);
        if (!enabled) {
            return InputOptimizeResponse.of(input, input, "", 0L, InputOptimizeResponse.STATUS_DISABLED);
        }

        long start = System.currentTimeMillis();
        try {
            // 解析优化模型（可能为 <providerId>:<model>）：请求 > input.optimizer_model > runtime.default_model
            String qualifiedModel = request.model();
            if (StringUtils.isBlank(qualifiedModel)) {
                qualifiedModel = globalSettingReader.getStr(GROUP, "optimizer_model");
            }
            if (StringUtils.isBlank(qualifiedModel)) {
                qualifiedModel = globalSettingReader.getStr(RUNTIME_GROUP, "default_model");
            }

            // 统一走「模型→客户端」解析器（按 DB 供应商动态构建，支持多密钥 failover），失败再回退注册中心默认
            ChatClient client = modelClientResolver.resolve(qualifiedModel);
            if (client == null) {
                client = chatClientRegistry.getDefault();
            }
            String model = ModelClientResolver.normalize(qualifiedModel);
            if (client == null) {
                // 静默回退过：这里原样返回原文且不打日志，调用方只能从「文本没变」去猜，
                // 于是前端把「没配可用模型」说成「模型认为已经够具体」。状态与日志都补上。
                log.warn("[InputOptimize] 没有可用的优化模型客户端（model={}），本次未调用模型", qualifiedModel);
                return InputOptimizeResponse.of(input, input, model, System.currentTimeMillis() - start,
                        InputOptimizeResponse.STATUS_UNAVAILABLE);
            }

            // 解析提示词：请求 > 全局配置 > 场景内置默认 > 通用内置默认
            String template = request.systemPrompt();
            if (StringUtils.isBlank(template)) {
                template = globalSettingReader.getStr(GROUP, "optimizer_prompt");
            }
            if (StringUtils.isBlank(template)) {
                template = SCENE_PROMPTS.get(
                        StringUtils.defaultString(request.scene(), "").trim().toLowerCase(Locale.ROOT));
            }
            if (StringUtils.isBlank(template)) {
                template = DEFAULT_OPTIMIZE_PROMPT;
            }

            // 含 %s 的模板视为「组合模板」：格式化后作为用户消息，另配固定 system 指令；
            // 不含 %s 的模板直接作为 system 指令，用户消息为原始输入。
            String systemInstruction;
            String userPrompt;
            if (template.contains("%s")) {
                userPrompt = String.format(template, input);
                systemInstruction = DEFAULT_SYSTEM_INSTRUCTION;
            } else {
                systemInstruction = template;
                userPrompt = input;
            }

            // 解析温度
            double temperature = globalSettingReader.getDouble(GROUP, "optimizer_temperature",
                    DEFAULT_OPTIMIZE_TEMPERATURE);

            String optimized = client
                    .model(model)
                    .system(systemInstruction)
                    .temperature(temperature)
                    .chatSync(userPrompt);

            if (StringUtils.isBlank(optimized)) {
                return InputOptimizeResponse.of(input, input, model, System.currentTimeMillis() - start,
                        InputOptimizeResponse.STATUS_EMPTY);
            }
            // 模型照抄原文时也要如实标注，别让调用方以为「这是一次成功的改写」
            String status = optimized.trim().equals(input.trim())
                    ? InputOptimizeResponse.STATUS_UNCHANGED
                    : InputOptimizeResponse.STATUS_OK;
            return InputOptimizeResponse.of(input, optimized, model, System.currentTimeMillis() - start, status);
        } catch (Exception e) {
            log.warn("[InputOptimize] 输入优化失败，返回原始输入: {}", e.getMessage());
            return InputOptimizeResponse.of(input, input, "", System.currentTimeMillis() - start,
                    InputOptimizeResponse.STATUS_FAILED);
        }
    }
}
