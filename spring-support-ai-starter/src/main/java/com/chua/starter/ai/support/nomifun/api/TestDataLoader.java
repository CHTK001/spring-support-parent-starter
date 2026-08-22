package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 启动时加载测试数据 - 从坚果云 secrets.html 提取
 */
@Slf4j
@Component
@Order(1)
public class TestDataLoader implements CommandLineRunner {

    private final ApiProviderController providerController;
    private final ApiChatController chatController;

    public TestDataLoader(ApiProviderController providerController, ApiChatController chatController) {
        this.providerController = providerController;
        this.chatController = chatController;
    }

    @Override
    public void run(String... args) {
        log.info("Loading test data from nutstore secrets...");
        loadProviders();
        loadConversations();
        log.info("Test data loaded.");
    }

    private void loadProviders() {
        createProvider("openai-official", "OpenAI 官方", "openai",
            "sk-pIOCKnZJa0oz0UivGoohfcVPMZm8H1FijqVBNTcyYXyiMTJl",
            "https://api.openai.com/v1",
            List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "o1-mini", "o1-preview", "text-embedding-3-small"));

        createProvider("openrouter", "OpenRouter", "openrouter",
            "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",
            "https://openrouter.ai/api/v1",
            List.of("deepseek/deepseek-chat", "deepseek/deepseek-coder", "openai/gpt-oss-20b:free",
                "anthropic/claude-3.5-sonnet", "google/gemini-2.0-flash", "qwen/qwen-2.5-72b-instruct"));

        createProvider("google-gemini", "Google Gemini", "gemini",
            "AIzaSyAWxmH0QqA0HkMA4m4ktn91YyqsoM24LvM",
            "https://generativelanguage.googleapis.com/v1beta/openai",
            List.of("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-pro"));

        createProvider("deepseek", "DeepSeek", "deepseek",
            "sk-E1QRuJc47ilRbQkCQrrbEiGsm8qieYdtZNBh4yguZIrl619t",
            "https://api.deepseek.com/v1",
            List.of("deepseek-v4-flash", "deepseek-v4-flash-free", "deepseek-ai/deepseek-v4-flash"));

        createProvider("zhipu-glm", "智谱 GLM", "zhipu",
            "sk-JClM5fvRyxxQZus68ci9pAA5ALXVVZo4",
            "https://open.bigmodel.cn/api/paas/v4",
            List.of("glm-4-flash", "glm-4.7-flash", "glm-4.6v-flash", "glm-4.1v-thinking-flash"));

        createProvider("siliconflow", "SiliconFlow", "siliconflow",
            "sk-fqfxjiyhpadgvdlkousbnztmuttiegwmgaymubnaxofwoxhx",
            "https://api.siliconflow.cn/v1",
            List.of("deepseek-ai/DeepSeek-OCR", "deepseek-ai/DeepSeek-V4-Pro", "deepseek/deepseek-v4-flash-free"));

        createProvider("nvidia-nim", "NVIDIA NIM", "nvidia",
            "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",
            "https://integrate.api.nvidia.com/v1",
            List.of("nvidia/nemotron-nano-12b-v2-vl:free", "nvidia/nemotron-3-embed-1b:free",
                "nvidia/llama-nemotron-embed-vl-1b-v2:free"));

        createProvider("anthropic-claude", "Anthropic Claude", "anthropic",
            "sk-ant-api03-ms-159a4ef8-7535-4380-bfe2-60205e29c4b5",
            "https://api.anthropic.com/v1",
            List.of("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229"));
    }

    private void createProvider(String id, String name, String factory, String apiKey, String baseUrl, List<String> models) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider_id", id);
        body.put("name", name);
        body.put("factory", factory);
        body.put("platform", factory);
        body.put("auth_scheme", "api_key");
        body.put("base_url", baseUrl);
        body.put("app_key", apiKey);
        body.put("app_secret", "");
        body.put("initial_model", Map.of("model", models.get(0), "name", models.get(0)));
        try {
            var result = providerController.createProvider(body);
            log.info("Provider {} [{}]: {}", name, id, result != null ? "OK" : "null");
        } catch (Exception e) {
            log.warn("Provider {} conflict or error: {}", id, e.getMessage());
        }
    }

    private void loadConversations() {
        createConversation("nomi", "Agent协作测试", List.of(
            "你好，请帮我分析一下这个项目代码结构",
            "好的，我看到这是一个Spring Boot多模块项目，包含spring-support-common-starter、spring-support-ai-starter等模块。需要我进一步分析具体功能吗？",
            "请帮我优化API响应格式，特别是分页结果",
            "建议统一使用PaginatedResult<T>格式，包含items、total、has_more字段。这样前端可以统一处理分页逻辑。",
            "能否给出具体的Java代码示例？",
            "当然，可以用record定义：public record PaginatedResult<T>(List<T> items, long total, boolean hasMore) {}"
        ));
        createConversation("nomi", "模型对比调研", List.of(
            "帮我对比一下DeepSeek V4和GPT-4o的性能差异",
            "DeepSeek V4在中文场景表现更优，成本更低；GPT-4o在多语言和复杂推理上更强。",
            "那在代码生成场景呢？",
            "代码生成方面，DeepSeek Coder系列和GPT-4o各有优势，DeepSeek在亚洲代码风格上微调过，GPT-4o通用性更好。",
            "有没有 benchmark 数据？",
            "可以参考 HumanEval、MBPP 等基准测试。DeepSeek V4在HumanEval上达到85%+，GPT-4o约90%。"
        ));
        createConversation("nomi", "知识库配置咨询", List.of(
            "我的知识库怎么配置向量存储？",
            "根据secrets.html中的配置，建议使用Zilliz Cloud Milvus Serverless作为向量存储。",
            "具体怎么接入？",
            "需要提供host、port、collection和token。我在配置中看到了你的Zilliz endpoint信息。",
            "好的，我来配置一下",
            "已配置完成。向量维度设为4，算法使用cosine相似度。"
        ));
    }

    private void createConversation(String type, String name, List<String> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("name", name);
        try {
            var convResult = chatController.create(body);
            if (convResult == null || convResult.getData() == null) {
                log.warn("Failed to create conversation: {}", name);
                return;
            }
            Map<String, Object> conv = convResult.getData();
            String convId = String.valueOf(conv.get("conversation_id"));

            for (int i = 0; i < messages.size(); i++) {
                Map<String, Object> msgBody = new LinkedHashMap<>();
                msgBody.put("content", messages.get(i));
                try {
                    chatController.sendMessage(convId, msgBody);
                } catch (Exception e) {
                    log.warn("Failed to send message {} to {}: {}", i, convId, e.getMessage());
                }
            }
            log.info("Conversation '{}': created with {} messages", name, messages.size());
        } catch (Exception e) {
            log.warn("Failed to create conversation '{}': {}", name, e.getMessage());
        }
    }
}
