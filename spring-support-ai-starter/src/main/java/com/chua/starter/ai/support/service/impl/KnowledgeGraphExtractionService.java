// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.model.KnowledgeGraphExtractionDTO;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagClientSetting;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.rag.RagClientManager;
import com.chua.starter.ai.support.model.KnowledgeGraphEntityDTO;
import com.chua.starter.ai.support.model.KnowledgeGraphRelationDTO;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.KnowledgeGraphService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识点关系抽取服务：用大模型从文本里抽实体与关系，异步写入知识图谱。
 *
 * <p>抽取放在独立线程池而不是上传请求里同步跑：上传接口当前要等向量化完成才返回，
 * 再串一次 LLM 往返会明显拉长响应（批量导入更明显）。抽取失败只记日志，不影响入库。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphExtractionService {

    /**
     * 开关分组。
     */
    private static final String GROUP = "rag";

    /**
     * 开关字段：是否抽取关系。
     */
    private static final String FIELD_ENABLED = "rag_relation_extract_enabled";

    /**
     * 开关字段：抽取使用的模型。
     */
    private static final String FIELD_MODEL = "rag_relation_extract_model";

    /**
     * 送入大模型的文本上限，超出部分截断，避免撑爆上下文。
     */
    private static final int MAX_INPUT_CHARS = 6000;

    /**
     * 抽取提示词。
     */
    private static final String PROMPT_TEMPLATE = """
            你是知识图谱抽取器。从下面这段文本中抽取实体和实体之间的关系。
            要求：
            1. 只输出 JSON，不要输出解释、不要加 Markdown 代码块标记。
            2. 实体类型 type 只能取以下值之一：person(人物)、location(地点)、event(事件)、organization(组织)、other(其它)。
            3. 关系的 source 和 target 必须恰好是上面 entities 里出现过的实体名称。
            4. 抽不出内容时返回 {"entities":[],"relations":[]}，不要编造。
            输出格式示例：
            {"entities":[{"name":"关羽","type":"person","description":"蜀汉名将"},{"name":"荆州","type":"location","description":""}],"relations":[{"source":"关羽","target":"荆州","type":"镇守"}]}
            待抽取文本：
            ---
            %s
            ---
            """;

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;
    /**
     * RAG 客户端管理器：首选它解析好的对话客户端（凭据/模型都已就绪）
     */
    private final RagClientManager ragClientManager;
    /**
     * 对话客户端注册表
     */
    private final ChatClientRegistry chatClientRegistry;
    /**
     * 知识图谱服务
     */
    private final KnowledgeGraphService knowledgeGraphService;

    /**
     * JSON 解析器。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 抽取线程池：有界队列 + 调用方执行，避免批量导入时无限堆积任务。
     */
// [P3C C1 豁免] 场景化创建线程，需文档化
    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 2, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(256),
            new NamedThreadFactory("kg-extract"),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /**
     * 关系提取开关是否开启。
     *
     * @return 开启返回 true
     */
    public boolean isEnabled() {
        try {
            return settingReader.getBool(GROUP, FIELD_ENABLED, true);
        } catch (Exception e) {
            // 配置读取失败不应阻断上传，按默认开启处理并留痕
            log.warn("[KnowledgeGraph] 读取关系提取开关失败，按默认开启处理: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 异步提交一个知识点的关系抽取任务。
     *
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeBaseName 知识库名称
     * @param docPath 知识点相对路径
     * @param text 知识点正文
     */
    public void submitAsync(String knowledgeBaseId, String knowledgeBaseName,
                            String docPath, String text) {
        if (!isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(knowledgeBaseId) || !StringUtils.hasText(text)) {
            return;
        }
        try {
            CompletableFuture.runAsync(() -> runExtraction(knowledgeBaseId, knowledgeBaseName, docPath, text), executor);
        } catch (Exception e) {
            log.warn("[KnowledgeGraph] 关系抽取任务提交失败 kb={} path={}: {}",
                    knowledgeBaseId, docPath, e.getMessage());
        }
    }

    /**
     * 同步抽取并落库，供重新抽取接口与自检使用。
     *
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeBaseName 知识库名称
     * @param docPath 知识点相对路径
     * @param text 知识点正文
     * @return 抽取结果；失败时返回 null
     */
    public KnowledgeGraphExtractionDTO extractAndStore(String knowledgeBaseId, String knowledgeBaseName,
                                                       String docPath, String text) {
        if (!StringUtils.hasText(knowledgeBaseId) || !StringUtils.hasText(text)) {
            return null;
        }
        return runExtraction(knowledgeBaseId, knowledgeBaseName, docPath, text);
    }

    /**
     * 仅执行抽取（不落库），用于排障时观察大模型输出。
     *
     * @param text 待抽取文本
     * @return 抽取结果
     */
    public KnowledgeGraphExtractionDTO extractOnly(String text) {
        ChatClient client = resolveClient();
        if (client == null) {
            log.warn("[KnowledgeGraph] 没有可用的对话客户端，跳过关系抽取");
            return KnowledgeGraphExtractionDTO.empty();
        }
        String input = text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;
        String prompt = String.format(PROMPT_TEMPLATE, input);
        // 这里刻意不设 temperature=0。
        // ChatClient 的 temperature() 是原地改字段并返回 this（见 OpenAiChatClient#temperature：
        // this.temperature = temperature; return this;），而 ChatClientRegistry 里的实例是
        // 进程级共享单例，newChat() 同样是清历史后 return this —— 没有任何隔离副本可用。
        // 在这里调 temperature(0.0) 会把**全应用**的对话（普通聊天、图像/视频生成、Agent、
        // 工具调用）一起永久钉成贪心解码，是跨模块的严重回归。
        // 代价是抽取结果不可复现（每次措辞略有差异），因此并集语义下重复抽取会让图谱缓慢膨胀。
        // 想要干净重抽请走 graph/generate?reset=true（先清空该知识点关联再抽），不要指望调温度。
        String reply = client.chatSync(prompt);
        return parse(reply);
    }

    /**
     * 抽取并落库的实际执行体。
     *
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeBaseName 知识库名称
     * @param docPath 知识点相对路径
     * @param text 知识点正文
     * @return 抽取结果；异常时返回 null
     */
    private KnowledgeGraphExtractionDTO runExtraction(String knowledgeBaseId, String knowledgeBaseName,
                                                      String docPath, String text) {
        long start = System.currentTimeMillis();
        try {
            KnowledgeGraphExtractionDTO extraction = extractOnly(text);
            if (extraction == null || extraction.isEmpty()) {
                log.info("[KnowledgeGraph] 未抽取到实体关系 kb={} path={} 耗时={}ms",
                        knowledgeBaseId, docPath, System.currentTimeMillis() - start);
                // 抽空时什么都不做：合并语义下若在这里清关联，LLM 一次空返回（超时/截断/格式错）
                // 就会抹掉该知识点已有的全部关系。宁可留着可能过期的关联，也不能因为一次抖动丢数据；
                // 真要清理走删除知识点（deleteByKnowledgePoint）。
                return extraction;
            }
            knowledgeGraphService.mergeKnowledgePointGraph(
                    knowledgeBaseId, knowledgeBaseName, docPath, extraction);
            log.info("[KnowledgeGraph] 关系入库完成 kb={} path={} 实体={} 关系={} 耗时={}ms",
                    knowledgeBaseId, docPath,
                    extraction.entities() == null ? 0 : extraction.entities().size(),
                    extraction.relations() == null ? 0 : extraction.relations().size(),
                    System.currentTimeMillis() - start);
            return extraction;
        } catch (Exception e) {
            // 抽取失败不影响知识入库，只记录
            log.warn("[KnowledgeGraph] 关系抽取失败 kb={} path={}: {}",
                    knowledgeBaseId, docPath, e.getMessage());
            return null;
        }
    }

    /**
     * 解析大模型返回的 JSON，容忍 Markdown 代码块与前后多余文本。
     *
     * @param reply 大模型原始回复
     * @return 抽取结果，解析失败时返回空结果
     */
    private KnowledgeGraphExtractionDTO parse(String reply) {
        if (!StringUtils.hasText(reply)) {
            return KnowledgeGraphExtractionDTO.empty();
        }
        String json = reply.trim();
        if (json.startsWith("```")) {
            int firstLine = json.indexOf('\n');
            if (firstLine > 0) {
                json = json.substring(firstLine + 1);
            }
            int fence = json.lastIndexOf("```");
            if (fence >= 0) {
                json = json.substring(0, fence);
            }
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("[KnowledgeGraph] 大模型未返回 JSON: {}", abbreviate(reply));
            return KnowledgeGraphExtractionDTO.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json.substring(start, end + 1));
            List<KnowledgeGraphEntityDTO> entities = new ArrayList<>();
            for (JsonNode node : root.path("entities")) {
                String name = node.path("name").asText("");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                entities.add(new KnowledgeGraphEntityDTO(name,
                        node.path("type").asText("other"),
                        node.path("description").asText("")));
            }
            List<KnowledgeGraphRelationDTO> relations = new ArrayList<>();
            for (JsonNode node : root.path("relations")) {
                String source = node.path("source").asText("");
                String target = node.path("target").asText("");
                if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                    continue;
                }
                relations.add(new KnowledgeGraphRelationDTO(source, target,
                        node.path("type").asText("")));
            }
            return new KnowledgeGraphExtractionDTO(entities, relations);
        } catch (Exception e) {
            log.warn("[KnowledgeGraph] 解析抽取结果失败: {} 原文={}", e.getMessage(), abbreviate(reply));
            return KnowledgeGraphExtractionDTO.empty();
        }
    }

    /**
     * 解析用于抽取的对话客户端：优先关系抽取专用模型，其次全局默认。
     *
     * @return 对话客户端；不可用时返回 null
     */
    private ChatClient resolveClient() {
        // 首选 RAG 自己那条已经解析好凭据与模型的链路：RAG 问答能跑通就说明这个客户端可用。
        // ChatClientRegistry 在本部署里可能是空的（只有注册过的 provider 才在里面），
        // 直接用它当主选会拿不到客户端、抽取被静默跳过。
        try {
            RagClient ragClient = ragClientManager.current();
            RagClientSetting setting = ragClient == null ? null : ragClient.getSetting();
            ChatClient fromRag = setting == null ? null : setting.getChatClient();
            if (fromRag != null) {
                return fromRag;
            }
        } catch (Exception e) {
            log.debug("[KnowledgeGraph] 从 RAG 客户端取对话客户端失败，回退注册表: {}", e.getMessage());
        }
        try {
            String model = settingReader.getStr(GROUP, FIELD_MODEL);
            if (StringUtils.hasText(model) && chatClientRegistry.getByModel(model.trim()) != null) {
                return chatClientRegistry.getByModel(model.trim());
            }
            return chatClientRegistry.getDefault();
        } catch (Exception e) {
            log.warn("[KnowledgeGraph] 解析对话客户端失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 截断长文本用于日志。
     *
     * @param text 原始文本
     * @return 最多 200 字符的片段
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ').trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "..." : flat;
    }

    /**
     * 关闭线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 带名字的线程工厂，便于在日志与 jstack 里定位抽取线程。
     */
    private static final class NamedThreadFactory implements ThreadFactory {

        /**
         * 线程名前缀。
         */
        private final String prefix;

        /**
         * 线程序号。
         */
        private final AtomicInteger counter = new AtomicInteger(1);

        /**
         * 构造线程工厂。
         *
         * @param prefix 线程名前缀
         */
        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
// [P3C C1 豁免] 守护/后台线程：有独立生命周期，不适合交给通用执行器
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }}