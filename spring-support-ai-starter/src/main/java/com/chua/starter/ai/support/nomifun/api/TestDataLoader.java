package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 启动时从坚果云 secrets.html 加载真实测试数据
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
        log.info("Loading real test data from Nutstore secrets...");
        loadAllProviders();
        loadRealConversations();
        log.info("Test data loaded: {} providers, multiple conversations", providerCount.get());
    }

    private final AtomicInteger providerCount = new AtomicInteger(0);

    private void loadAllProviders() {
        // === AI 模型提供商 ===
        createProvider("agnes-ai", "Agnes AI", "openai",
            "sk-pIOCKnZJa0oz0UivGoohfcVPMZm8H1FijqVBNTcyYXyiMTJl",
            "https://apihub.agnes-ai.com/v1",
            List.of("agnes-2.0-flash", "agnes-image-2.0-flash", "agnes-image-2.1-flash", "agnes-video-v2.0"));

        createProvider("openai-official", "OpenAI 官方", "openai",
            "sk-pIOCKnZJa0oz0UivGoohfcVPMZm8H1FijqVBNTcyYXyiMTJl",
            "https://api.openai.com/v1",
            List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "o1-mini", "o1-preview", "text-embedding-3-small"));

        createProvider("openrouter", "OpenRouter", "openrouter",
            "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",
            "https://openrouter.ai/api/v1",
            List.of("deepseek/deepseek-chat", "deepseek/deepseek-coder", "openai/gpt-oss-20b:free",
                "anthropic/claude-3.5-sonnet", "google/gemini-2.0-flash", "qwen/qwen-2.5-72b-instruct",
                "inclusionai/ling-3.0-flash:free", "nvidia/nemotron-3-embed-1b:free",
                "nvidia/llama-nemotron-embed-vl-1b-v2:free"));

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
            List.of("glm-4-flash", "glm-4.7-flash", "glm-4.6v-flash", "glm-4.1v-thinking-flash",
                "cogview-3-flash", "cogvideox-flash"));

        createProvider("sensenova", "SenseNova 商汤", "sensenova",
            "sk-JClM5fvRyxxQZus68ci9pAA5ALXVVZo4",
            "https://token.sensenova.cn/v1",
            List.of("sensenova-6.7-flash-lite", "sensenova-u1-fast"));

        createProvider("huiqing-ai", "慧清 AI", "huiqing",
            "sk-E1QRuJc47ilRbQkCQrrbEiGsm8qieYdtZNBh4yguZIrl619t",
            "https://ai.huiqing.cyou/v1",
            List.of("qwen/qwen3-next-80b-a3b-instruct:free", "qwen/qwen3-coder:free",
                "openai/gpt-oss-20b:free", "nvidia/nemotron-nano-12b-v2-vl:free"));

        createProvider("siliconflow", "SiliconFlow 硅基流动", "siliconflow",
            "sk-fqfxjiyhpadgvdlkousbnztmuttiegwmgaymubnaxofwoxhx",
            "https://api.siliconflow.cn/v1",
            List.of("deepseek-ai/DeepSeek-OCR", "PaddlePaddle/PaddleOCR-VL-1.5",
                "tencent/Hunyuan-MT-7B", "Kwai-Kolors/Kolors",
                "BAAI/bge-large-en-v1.5", "BAAI/bge-large-zh-v1.5", "BAAI/bge-m3"));

        createProvider("nvidia-nim", "NVIDIA NIM", "nvidia",
            "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",
            "https://integrate.api.nvidia.com/v1",
            List.of("deepseek-ai/deepseek-v4-flash", "nvidia/nemotron-nano-12b-v2-vl:free",
                "nvidia/nemotron-3-embed-1b:free", "nvidia/llama-nemotron-embed-vl-1b-v2:free"));

        createProvider("huggingface", "Hugging Face", "huggingface",
            "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",
            "https://hf-mirror.com",
            List.of("cn-clip-image", "cn-clip-text", "ada-face", "depth-anything",
                "vit-pose", "swinir", "codeformer", "sam-encoder", "grounding-dino",
                "lama-inpainting", "anime-face-detector", "clipseg-zero-shot"));

        createProvider("modelscope", "魔搭 ModelScope", "modelscope",
            "ms-159a4ef8-7535-4380-bfe2-60205e29c4b5",
            "https://api-inference.modelscope.cn/v1",
            List.of("deepseek-ai/DeepSeek-V4-Pro"));

        createProvider("bai", "b.ai", "bai",
            "sk-17gk0zim0s1d3tpziwkadvfnv5blb7t4",
            "https://api.b.ai/v1",
            List.of("deepseek-v4-flash"));

        createProvider("arcsoft", "arcsoft 虹软", "arcsoft",
            "9gN1dRr4QVGZztS8iqwc2sBiLDGRUjRgfj3BiZsX21wk",
            "",
            List.of("face-restoration", "face-detection", "age-gender"));

        // === 企业/开发工具 ===
        createProvider("github", "GitHub", "github",
            "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",
            "https://api.github.com",
            List.of("repos", "issues", "pull-requests"));

        createProvider("aliyun-rc", "阿里云效", "aliyun",
            "pt-XQ4g3mBv4SxGVJ03ek6Z8v0J_5c924262-1124-40ac-ad3d-619add0d15c5",
            "https://devops.aliyun.com",
            List.of("pipelines", "artifacts"));

        createProvider("ngrok", "ngrok", "ngrok",
            "3H7bWwc4cuqZuJLt7LPR2KmtO5M_GW2jbQ96wpyzfNS2NrHp",
            "https://api.ngrok.com",
            List.of("tunnels", "sessions"));

        createProvider("cloudflare", "Cloudflare", "cloudflare",
            "cfut_GJTkvKOxl0bVJHH5jEmYLmkvD0IhkSSpb71b889Be3f2d549",
            "https://api.cloudflare.com",
            List.of("dns", "workers", "r2"));

        createProvider("zilliz", "Zilliz Cloud", "zilliz",
            "4f7aa5079132e5e7686f6276ad336aa0160e21c969bad84de3a72f285a350ac9ea931a8469c4288c6a33475a64ed655ab01d97e7",
            "https://in03-a2f450287f79a4e.serverless.gcp-us-west1.cloud.zilliz.com:443",
            List.of("vector-store", "milvus"));

        createProvider("notion", "Notion", "notion",
            "nt_784412312qq_com_ahf5jhu82i2gc44t",
            "https://api.notion.com/v1",
            List.of("pages", "databases"));
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
            if (result != null) providerCount.incrementAndGet();
            log.info("Provider loaded: {} [{}]", name, id);
        } catch (Exception e) {
            log.warn("Provider {} may already exist: {}", id, e.getMessage());
        }
    }

    private void loadRealConversations() {
        // 场景1: 项目架构分析与优化
        createConversation("nomi", "Spring Boot多模块架构分析", List.of(
            "帮我分析当前项目的模块结构和依赖关系",
            "好的，我看到这是一个Spring Boot多模块Maven项目。核心模块包括：spring-support-parent-starter（父工程）、spring-support-common-starter（通用工具）、spring-support-ai-starter（AI服务）。各模块通过Maven dependency管理版本，使用<module>标签声明。",
            "请帮我检查是否有循环依赖问题",
            "检查完成。发现 spring-support-ai-starter 依赖 spring-support-common-starter，而 common 不反向依赖 ai。没有循环依赖。但建议将 AI 相关的通用类提取到单独的 ai-common 模块，避免 AI 模块膨胀。",
            "如何优化构建速度？",
            "建议：1) 使用增量编译 - 确保 Lombok 注解处理器正确配置；2) 启用 Maven 并行构建 -pl 指定子模块；3) 使用本地缓存 -Dmaven.repo.local；4) 考虑使用 Gradle 替代 Maven 以获得更好的增量构建。",
            "请给出具体的 Maven 优化配置",
            "已在 pom.xml 中添加以下优化：1) <parallel>threads</parallel> 启用多线程；2) <usePluginRegistry>true</usePluginRegistry> 复用插件；3) 配置本地仓库到 D:/ch/repository；4) 添加 <skipTests>true</skipTests> 跳过测试加速构建。",
            "还需要配置哪些环境变量？",
            "建议设置 JAVA_HOME 指向 Corretto JDK 25，MAVEN_OPTS 设置为 -Xmx512m -XX:+UseParallelGC，同时配置阿里云 Maven 镜像加速依赖下载。"
        ));

        // 场景2: 模型选型与API集成
        createConversation("nomi", "多模型API集成方案选型", List.of(
            "我们项目中需要集成多个AI模型提供商，该如何设计？",
            "建议采用统一适配器模式：1) 定义标准 AiChatRequest/AiChatResponse 接口；2) 每个提供商实现 Adapter 接口；3) 使用工厂模式根据 platform 字段路由；4) 支持流式和非流式两种模式。",
            "OpenAI 和 DeepSeek 的 API 有什么不同？",
            "主要差异：1) DeepSeek 支持更长的上下文窗口（128K vs 128K）；2) DeepSeek 的 base_url 不同；3) API Key 格式不同；4) DeepSeek 有专门的 coder 模型系列。响应格式基本兼容 OpenAI 标准。",
            "如何处理 API 限流和重试？",
            "建议实现：1) 令牌桶限流器，按提供商配置 QPM；2) 指数退避重试，最多3次；3) 熔断器模式，失败率超50%时熔断30秒；4) 降级策略，主提供商不可用时自动切换到备用。",
            "能否给出 RetryTemplate 配置示例？",
            "使用 Spring Retry：@Retryable(maxAttempts=3, backoff=@Backoff(delay=1000, multiplier=2))，对 RateLimitException 和 ServiceUnavailableException 生效。同时配置 ThreadPoolTaskExecutor 控制并发。",
            "成本怎么估算？",
            "OpenAI GPT-4o: $2.50/1M input, $10/1M output；DeepSeek V4: ~$0.14/1M input, $0.28/1M output；Gemini 2.0 Flash: 免费额度后 $0.10/$0.40。建议先用免费额度测试，再按比例付费。"
        ));

        // 场景3: 知识库/RAG配置
        createConversation("nomi", "向量数据库配置与知识库搭建", List.of(
            "我的知识库需要配置向量存储，有什么推荐？",
            "根据配置，推荐使用 Zilliz Cloud Milvus Serverless。优势：1) 免运维；2) 自动扩缩容；3) 支持 cosine 相似度；4) 提供 Python/Java SDK。维度设为 1536（text-embedding-3-small 输出）。",
            "具体的 SPI 配置是什么？",
            "代码配置如下：VectorStorageProvider.of(\"milvus\").dimension(1536).algorithm(VectorCompareAlgorithm.cosine()).properties(new MilvusStorageProperties().setHost(\"https://in03-a2f450287f79a4e.serverless.gcp-us-west1.cloud.zilliz.com:443\").setPort(443).setCollection(\"vector_store\").setToken(\"your-token\")).build();",
            "如何批量导入文档？",
            "使用文档分割策略：1) 按段落分割（paragraph），最大1000tokens；2) 重叠10%；3) 使用 text-embedding-3-small 生成向量；4) 批量插入，每批100条；5) 创建 IVF_FLAT 索引，nlist=1024。",
            "检索效果不好怎么办？",
            "调优建议：1) 增加 overlap 到 15%；2) 尝试 mixed 查询（关键词+向量混合检索）；3) 对检索结果做 Rerank（使用 BAAI/bge-reranker-v2-m3）；4) 调整 top_k 从 5 到 10。"
        ));

        // 场景4: 企业微信机器人集成
        createConversation("nomi", "企业微信机器人接入方案", List.of(
            "我们想用企业微信做 AI 助手，怎么接入？",
            "使用 ClawX 企业微信方案：1) 配置 CorpId 和 AgentId 获取 Access Token；2) 注册消息回调 URL；3) 接收事件推送；4) 调用 AI 接口生成回复；5) 通过企业微信 API 发送消息。注意权限范围和消息长度限制。",
            "Access Token 有效期多久？",
            "Access Token 有效期 7200 秒（2小时），建议缓存并定时刷新。CorpSecret 需要妥善保管，不要硬编码在代码中，使用环境变量或密钥管理服务。",
            "如何支持图文消息？",
            "企业微信支持 text、markdown、image、news 四种消息类型。AI 回复建议用 markdown 格式，支持加粗、链接、代码块。图片消息需要先上传到企业微信素材库获取 media_id。"
        ));

        // 场景5: GitHub PR 自动化
        createConversation("nomi", "GitHub PR 自动化流程", List.of(
            "如何用 GitHub API 自动化 PR 流程？",
            "流程：1) Webhook 监听 push 事件；2) 触发 CI/CD Pipeline；3) 自动生成代码审查评论；4) 检查代码规范（Checkstyle/PMD）；5) 更新 PR 状态；6) 合并前要求至少 1 个 Approve。",
            "如何配置 GitHub Token 权限？",
            "建议使用 Fine-grained Token，仅授予 repository:contents + repository:pull-requests + repository:statuses 权限。不要使用 Classic Token 全量权限。",
            "PR 描述模板怎么设计？",
            "使用 PR Template：\\`\\`\\`markdown\\n## Type\\n- [ ] Bug fix\\n- [ ] Feature\\n- [ ] Refactor\\n\\n## Checklist\\n- [ ] Tests added\\n- [ ] Docs updated\\n- [ ] Changelog entry\\n\\`\\`\\`"
        ));

        // 场景6: CDN 部署与缓存策略
        createConversation("nomi", "Cloudflare CDN 部署与缓存优化", List.of(
            "前端资源如何通过 Cloudflare CDN 加速？",
            "步骤：1) CNAME DNS 记录指向 *.pages.dev；2) 开启 Auto Minify（JS/CSS/HTML）；3) 设置 Cache Rules：静态资源缓存 1年，API 请求不缓存；4) 开启 Brotli 压缩；5) 配置 Page Rule 缓存级别设为 'Cache Everything'。",
            "R2 对象存储怎么配合使用？",
            "R2 作为静态资源存储，通过 Custom Domain 映射。配合 Workers 实现边缘计算缓存。带宽成本低，无 egress 费用。建议对图片做自动转 WebP，视频做自适应码率。",
            "D1 数据库适合什么场景？",
            "D1 是 Cloudflare 的 Serverless SQL 数据库，适合低流量、读写分离的场景。不适合高并发写入。建议用于存储配置、用户偏好等轻量数据。"
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
            log.info("Conversation '{}' created with {} messages [id={}]", name, messages.size(), convId);
        } catch (Exception e) {
            log.warn("Failed to create conversation '{}': {}", name, e.getMessage());
        }
    }

}
