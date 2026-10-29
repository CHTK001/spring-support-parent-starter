package com.chua.starter.ai.support.configuration.rag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.ocr.OcrClientManager;
import com.chua.starter.ai.support.configuration.ocr.OcrPipelineTextExtractor;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.MemoryChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.common.support.ai.embedding.EmbeddingClientSetting;
import com.chua.common.support.ai.embedding.MemoryEmbeddingClient;
import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagClientProvider;
import com.chua.common.support.ai.rag.RagClientSetting;
import com.chua.common.support.ai.rag.RagPipeline;
import com.chua.starter.ai.support.configuration.rag.RagSettingsChangedEvent;
import com.chua.common.support.ai.splitter.SentenceTextSplitter;
import com.chua.common.support.ai.splitter.TextSplitter;
import com.chua.common.support.ai.splitter.TextSplitterProvider;
import com.chua.common.support.file.txtractor.TextExtractor;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageDescriptor;
import com.chua.common.support.vector.VectorStorageField;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.jvector.support.spi.JVectorVectorStorageProvider;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.starter.ai.support.chat.ModelClientResolver;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import com.chua.starter.oauth.client.support.user.UserResume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 客户端管理器（<b>按账号隔离</b>）。
 *
 * <p>引入 AI 模块即提供 RAG 客户端，配置读取自 {@code sys_ai_global_setting}
 * 键值表（分组 {@code rag}：向量模型、向量数据库、上传目录、RAG 系统提示词、RAG LLM 模型、
 * 切片大小、切片重叠、OCR 模型），并叠加 {@code GlobalSettingReader} 的「用户覆盖优先」：
 * 每个账号取到自己的配置。</p>
 *
 * <p><b>隔离到什么程度</b>：embedding 客户端、向量索引文件、分块内容缓存、上传目录
 * 全部按 userId 分目录（{@code <rag_upload_dir>/<账号>/rag}），所以
 * ① 两个账号互不检索到对方的分块；
 * ② 一个账号换 embedding 维度不会破坏另一个账号的索引；
 * ③ 用户级设置改动只重建触发者自己的客户端（{@link #reloadUser}），
 * 不会被烘进别人的实例。全局配置改动才重建全部（{@link #rebuild()}），无需重启。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagClientManager implements RagClientProvider {

    /**
     * 与默认 内存嵌入客户端 输出维度一致。
     */
    private static final int DEFAULT_EMBEDDING_DIMENSIONS = 1536;

    /**
     * 全局配置组名
     */
    private static final String GROUP = "rag";

    /**
     * 向量库配置项：JSON，结构为「SPI 名称 → 键值配置」。
     *
     * <p>按名称分桶保存，切换向量库时各自身份的 host / token / 表名等配置都保留，
     * 不会被另一种向量库覆盖。</p>
     */
    public static final String KEY_VECTOR_STORAGE_CONFIG = "rag_vector_storage_config";


    /**
     * 全局 RAG 客户端在 Spring 容器中的 Bean 名称（保留给无用户上下文的调用方，如健康检查）。
     */
    private static final String RAG_CLIENT_BEAN_NAME = "globalRagClient";

    /**
     * 按账号隔离 RAG 资源时，每个账号索引文件所在的子目录名。
     *
     * <p>取 {@code rag} 而不是直接用账号名，是为了让同一账号的索引、上传目录、
     * 分块内容缓存聚在一个目录下，便于备份与清理。</p>
     */
    private static final String ACCOUNT_LEAF = "rag";

    /**
     * jvector 索引文件名（jvector 自行追加 {@code .vectors}）。
     */
    private static final String INDEX_NAME = "jvector-index";

    /**
     * 命名空间型落盘位置可能用到的字段键（表名 / 集合名）。
     *
     * <p>按描述器是否声明这些键来决定能否按账号隔离，不硬编码具体向量库名：
     * mysql / postgresql 声明 {@code tableName}，milvus 声明 {@code collection}。</p>
     */
    private static final List<String> NAMESPACE_FIELD_KEYS = List.of("tableName", "collection");

    /**
     * 命名空间字段未配置时使用的默认名（与各向量库实现自身的默认值一致）。
     */
    private static final String DEFAULT_NAMESPACE = "vector_store";

    /**
     * 账号后缀的最大长度：{@code vector_store}(13) + 下划线 + 后缀 + 可选哈希 ≤ 64。
     */
    private static final int MAX_NAMESPACE_SUFFIX = 40;

    /**
     * 无用户上下文时使用的账号标识占位值（健康检查等场景）。
     *
     * <p>这些调用没有登录身份，既读不到用户覆盖也定位不到个人索引。
     * 单独给一个占位桶，而不是回落到管理员的桶，避免健康检查在管理员
     * 的索引上做 {@code rebuild()} 之类的写操作。</p>
     */
    private static final String SYSTEM_ACCOUNT = "_system";

    /**
     * 账号客户端缓存的上限（防止账号数无限增长把内存吃光）。
     */
    private static final int MAX_USER_CLIENTS = 64;

    /**
     * 本地推理类 embedding 引擎：不需要 apiKey / baseUrl 即可构建。
     *
     * <p>这些引擎由 {@code META-INF/extensions/com.chua.common.support.ai.embedding.EmbeddingClient}
     * 索引暴露，实际模型走 deeplearning 的 ModelRegistry 解析（可指向本地 ONNX 目录），
     * 不经过任何 HTTP 服务。若对它们也套用「必须有 apiKey + baseUrl」的门槛，
     * 会把「已注册、模型已就位」误判为不可用并静默降级成占位向量。</p>
     */
    private static final Set<String> LOCAL_EMBEDDING_ENGINES =
            Set.of("memory", "onnx", "bge", "minilm", "wemm");

    /**
     * 本地推理类 embedding 引擎标识集合（只读）。
     *
     * <p>这些引擎无需 apiKey / baseUrl，由本地 ONNX 模型或内置实现直接推理。
     * 对外暴露给「引擎列表」这类只读展示接口使用：调用方原本去查
     * {@code VectorService} SPI，而该 SPI 并没有任何实现，
     * 会导致引擎列表恒为空、前端下拉框无选项。</p>
     *
     * @return 本地引擎标识集合
     */
    public Set<String> localEmbeddingEngines() {
        return LOCAL_EMBEDDING_ENGINES;
    }

    /**
     * embedding 配置自检：返回问题描述，{@code null} 表示配置完整。
     *
     * <p>为什么需要单独自检：{@code /v2/rag/status} 的 {@code available} 只校验
     * ChatClient 与向量存储能否构建，<b>不校验 embedding</b>。embedding 静默回落成
     * {@link MemoryEmbeddingClient}（维度全 0、检索质量为零）时状态照样报「正常」，
     * 会把故障掩盖掉。这里按「引擎 → SPI 是否注册 → 模型 id → 凭证」逐层判，
     * 与 {@link #createEmbeddingClient(String, int)} 的回落条件一一对应。</p>
     *
     * @return 问题描述；配置完整时返回 null
     */
    public String embeddingIssue() {
        String model = settingReader.getStr(GROUP, "rag_embedding_model_name");
        if (!StringUtils.hasText(model)) {
            return "未配置 Embedding 模型：请到「设置中心 → 默认模型 → RAG 知识库」选择一个 Embedding 模型"
                    + "（引擎由所选模型自动推导，不需要手动指定）";
        }
        String engine = resolveEmbeddingEngineByModel(model);
        if (!StringUtils.hasText(engine)) {
            return "Embedding 模型『" + model + "』既不在本地模型库，其供应商也未提供 embedding 能力，"
                    + "无法确定引擎；请改选一个可用的 Embedding 模型";
        }
        Set<String> providers = ServiceProvider.of(EmbeddingClient.class).getExtensions();
        boolean registered = false;
        for (String provider : providers) {
            if (provider != null && provider.equalsIgnoreCase(engine)) {
                registered = true;
                break;
            }
        }
        if (!registered) {
            return "由模型『" + model + "』推导出的引擎『" + engine + "』未在 SPI 注册；已注册: " + providers;
        }
        boolean local = LOCAL_EMBEDDING_ENGINES.contains(engine.toLowerCase());
        if (local) {
            return null;
        }
        // 三方引擎：与 createEmbeddingClient 一致，允许回落到 runtime 的凭证
        String key = settingReader.getStr(GROUP, "rag_embedding_api_key");
        String base = settingReader.getStr(GROUP, "rag_embedding_base_url");
        if (!StringUtils.hasText(key)) {
            key = settingReader.getStr("runtime", "provider_api_key");
        }
        if (!StringUtils.hasText(base)) {
            base = settingReader.getStr("runtime", "provider_base_url");
        }
        if (!StringUtils.hasText(key) || !StringUtils.hasText(base)) {
            return "Embedding 模型『" + model + "』走三方引擎『" + engine + "』，缺少 apiKey / baseUrl"
                    + "（RAG 设置或 runtime 都未配置）";
        }
        return null;
    }

    /**
     * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
     * 全局 对话客户端 注册表（按模型解析 RAG LLM）
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
     * RAG 配置属性（默认值兜底）
     */
    private final RagProperties properties;

    /**
     * Spring Bean工厂：用于动态注册/替换 全局rag客户端 Bean。
     */
    private final DefaultListableBeanFactory beanFactory;

    /**
     * 共享 OCR 管线（ocr 分组为单一事实来源）：RAG 图片文字提取直接复用
     * {@link OcrClientManager}，不再各自构建管线，配置热更新由它负责。
     */
    private final OcrClientManager ocrClientManager;

    /**
     * 宿主数据源，供「向量直接存业务库」的向量库实现注入。
     *
     * <p>用 {@code ObjectProvider} 延迟取，避免 jvector / milvus 等不需要数据源时
     * 因为容器里没有 DataSource 而启动失败。</p>
     */
    private final ObjectProvider<DataSource> dataSourceProvider;

    /**
     * LLM 供应商表 Mapper：按供应商ID解析 base_url 与 API Key。
     *
     * <p>模型引用形如 {@code 供应商ID:模型名}（如 {@code 1789547286889:agnes-3.0-flash}），
     * 其凭据权威来源是 {@code sys_ai_provider}，而非 {@code runtime} 分组的
     * provider/provider_api_key —— 后者由 {@code RuntimeGlobalSettingProvider} 以
     * {@code openai}/{@code gpt-4o} 作为首次种子默认值，供应商接入信息并不落在那里。</p>
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 生效对话模型解析器：为 RAG 提供「当前用户默认模型 → Bot → 全局默认」的兜底链。
     */
    private final EffectiveChatModelResolver effectiveChatModelResolver;

    /**
     * 供应商模型表 Mapper：把「裸模型名」定位到其供应商，再按供应商凭据构建 ChatClient。
     */
    private final SysAiProviderModelMapper providerModelMapper;
    private final ModelClientResolver modelClientResolver; // 渠道路由解析器（多账号 429 failover）


    /**
     * 每个账号一个 RAG 客户端：索引 / 分块缓存 / embedding 都按账号独立。
     *
     * <p>账号为 null 的系统桶放在 {@link #systemClient}，不放进这里。</p>
     *
     * <p>用 {@link ConcurrentHashMap} 而非 {@code LinkedHashMap}：读路径
     * {@link #current()} 不加锁，避免一个账号首次构建（要加载 ONNX 模型、可能耗时数秒）
     * 时把其它账号的读请求一起阻塞。代价是它不保证插入顺序，淘汰顺序改由
     * {@link #userOrder} 维护。</p>
     */
    private final Map<String, RagClient> userClients = new ConcurrentHashMap<>();

    /**
     * 账号的插入顺序（队首最旧），仅在 {@link #clientLock} 内访问。
     *
     * <p>存在的理由：{@link ConcurrentHashMap} 的迭代顺序既不稳定、也不代表使用顺序，
     * 直接拿它淘汰会变成「随机删」。而按「最近最少使用」淘汰需要维护访问时间，
     * 每次问答都更新时间戳会把热账号的锁竞争放大；RAG 客户端本身已经按账号常驻，
     * 按插入顺序淘汰（重建过的账号视为最新，重新排到队尾）已足够，
     * 且语义可预期。</p>
     */
    private final Deque<String> userOrder = new ArrayDeque<>();

    /**
     * 客户端缓存的统一锁。
     *
     * <p>必须统一：曾经「读路径加 {@code synchronized (userClients)}、而
     * {@code rebuildUser} 不加锁」，两者并发时会产出一个既没被 {@link #current()}
     * 持有、也没被 {@code rebuildUser} 持有的孤儿客户端——它不会被 close，
     * 向量索引不落盘，且可能与另一个实例同时写同一个索引文件。
     * 现在所有增删改（含 {@link #rebuild()}、{@link #destroy()}）都在这把锁内完成。</p>
     */
    private final Object clientLock = new Object();

    /**
     * 无用户上下文（健康检查 / 状态探针）时使用的 RAG 客户端（懒构建，热更新时替换）。
     */
    private volatile RagClient systemClient;

    /**
     * 获取当前用户生效的 RAG 客户端，未构建时按该用户配置懒构建。
     *
     * <p>模型与索引都已按 userId 隔离：不同账号各有自己的 embedding 与
     * 向量索引，换 embedding 模型 / 维度时不会互相破坏，也不会检索到别人的分块。</p>
     *
     * @return RAG 客户端（配置缺失时可能为空）
     */
    @Override
    public RagClient current() {
        String userId = AiUserContext.currentUserId();
        if (!StringUtils.hasText(userId)) {
            return systemRag();
        }
        // 快路径：已构建过就无锁返回，避免高并发问答都挤到锁上
        RagClient cached = userClients.get(userId);
        if (cached != null) {
            return cached;
        }
        synchronized (clientLock) {
            RagClient client = userClients.get(userId);
            if (client == null) {
                client = build(userId);
                if (client != null) {
                    putUserClient(userId, client);
                    evictOverflowUsers();
                    log.info("[RagClientManager] 已为账号 {} 构建独立 RAG 客户端（当前账号数 {}）",
                            userId, userClients.size());
                }
            }
            return client;
        }
    }

    /**
     * 无用户上下文（健康检查 / 状态探针）时的客户端，落在独立的系统桶。
     *
     * @return RAG 客户端；构建失败时返回空
     */
    private RagClient systemRag() {
        RagClient system = systemClient;
        if (system != null) {
            return system;
        }
        synchronized (clientLock) {
            if (systemClient == null) {
                systemClient = build(null);
                registerBean(systemClient);
            }
            return systemClient;
        }
    }

    /**
     * 登记账号客户端并把该账号排到插入序队尾（重建过的账号视为最新）。
     *
     * <p>调用方必须持有 {@link #clientLock}。</p>
     *
     * @param userId 账号标识，非空
     * @param client 客户端，非空
     */
    private void putUserClient(String userId, RagClient client) {
        userClients.put(userId, client);
        userOrder.remove(userId);
        userOrder.addLast(userId);
    }

    /**
     * 账号数超过 {@link #MAX_USER_CLIENTS} 时关闭并移除<b>最早构建</b>的客户端。
     *
     * <p>按 {@link #userOrder} 的插入顺序淘汰，不是「字典序最小」也不是随机：
     * {@link ConcurrentHashMap} 的迭代顺序不代表时间，直接拿它挑会变成随机删。
     * 淘汰时先 {@code close()} 让向量索引与分块缓存落盘，否则该账号数据会丢。</p>
     *
     * <p>调用方必须持有 {@link #clientLock}。</p>
     */
    private void evictOverflowUsers() {
        while (userClients.size() > MAX_USER_CLIENTS && !userOrder.isEmpty()) {
            String oldest = userOrder.pollFirst();
            RagClient evicted = userClients.remove(oldest);
            if (evicted == null) {
                // 顺序队列里的残留项（该账号已被重建移除），继续看下一个
                continue;
            }
            closeQuietly(evicted);
            log.info("[RagClientManager] 账号客户端超出上限 {}，已关闭最早构建的账号 {}（索引已落盘，"
                    + "下次访问会按当前配置重新构建）", MAX_USER_CLIENTS, oldest);
        }
    }

    /**
     * 关闭客户端并吞掉异常（关闭失败不应影响主流程）。
     *
     * @param client 待关闭的客户端，允许为 {@code null}
     */
    private void closeQuietly(RagClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("[RagClientManager] 关闭 RAG 客户端失败: {}", e.getMessage());
        }
    }

    /**
     * 按最新全局配置重建 RAG 客户端（热更新）。
     *
     * <p>全局配置（向量库类型、切片、上传目录…）影响每个账号，所以重建全部已缓存账号；
     * 用户级改动走 {@link #reloadUser} 定向重建触发者那一个，
     * 否则会把他的覆盖烘进别人的客户端。</p>
     */
    public void rebuild() {
        synchronized (clientLock) {
            log.info("[RagClientManager] 重建 RAG 客户端（已缓存账号数 {}）", userClients.size());
            for (String userId : new ArrayList<>(userOrder)) {
                rebuildUserLocked(userId);
            }
            RagClient oldSystem = systemClient;
            RagClient nextSystem = build(null);
            systemClient = nextSystem;
            registerBean(nextSystem);
            if (oldSystem != null) {
                closeQuietly(oldSystem);
            }
        }
    }

    /**
     * 应用就绪后重建全部客户端。
     *
     * <p><b>为什么必须有这一步</b>：ONNX 外部模型根目录由
     * {@code OnnxExternalDirectoryService} 在启动期设置，而 Tomcat 在那之前就已开始接请求。
     * 若某个账号的客户端在这之前被懒构建，{@code createEmbeddingClient} 与
     * {@code resolveTextExtractor} 会把<b>当时的默认模型根</b>烘进该账号的客户端
     * （例如解析成 {@code models/onnx/arc-face.onnx}），此后 embedding 与图片文字提取
     * 全部失败，且没有任何异常——配置没变、也没有任何重建机会。
     * 人脸侧已实测过这条竞态（账号库先建 05:27:56、模型根后设 05:27:58）。
     * 按账号懒构建让「第一个访问者」决定客户端质量，所以必须在就绪后统一重建一次。</p>
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (userClients.isEmpty() && systemClient == null) {
            return;
        }
        log.info("[RagClientManager] 应用就绪，重建全部 RAG 客户端以采用最终的 ONNX 模型根");
        rebuild();
    }

    /**
     * 重建单个账号的 RAG 客户端（用户级设置变更）。
     *
     * @param userId 账号标识；为空表示无归属，此时不重建，避免按错误身份重建
     */
    private void reloadUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            log.debug("[RagClientManager] 收到用户级 rag 配置写入但事件未带账号，跳过重建");
            return;
        }
        synchronized (clientLock) {
            if (!userClients.containsKey(userId)) {
                // 该账号还没建过客户端，下次用到时按最新配置懒构建即可
                return;
            }
            rebuildUserLocked(userId);
        }
    }

    /**
     * 重建并替换指定账号的客户端（旧客户端先落盘再关闭）。
     *
     * <p>调用方必须持有 {@link #clientLock}：不持锁会让本方法与 {@link #current()}
     * 的懒构建并发，产出一个谁都不持有的孤儿客户端（不关闭、索引不落盘、
     * 且可能与另一个实例并发写同一索引文件）。</p>
     *
     * @param userId 账号标识，非空
     */
    private void rebuildUserLocked(String userId) {
        RagClient old = userClients.remove(userId);
        userOrder.remove(userId);
        RagClient next = build(userId);
        if (next == null) {
            log.warn("[RagClientManager] 账号 {} 重建后不可用，已移除其客户端", userId);
            return;
        }
        putUserClient(userId, next);
        closeQuietly(old);
    }

    /**
     * 通过 Bean工厂 动态注册 {@code globalRagClient} 单例 Bean，供容器内按名称获取最新实例。
     *
     * @param client RAG 客户端
     */
    private void registerBean(RagClient client) {
        try {
            if (beanFactory.containsSingleton(RAG_CLIENT_BEAN_NAME)) {
                beanFactory.destroySingleton(RAG_CLIENT_BEAN_NAME);
            }
            if (client != null) {
                beanFactory.registerSingleton(RAG_CLIENT_BEAN_NAME, client);
            }
        } catch (Exception e) {
            log.warn("[RagClientManager] BeanFactory 注册 RAG 客户端失败: {}", e.getMessage());
        }
    }

    /**
     * 基于指定账号的配置构建 RAG 客户端（索引与上传目录按账号隔离）。
     *
     * <p>整段构建都在 {@code userId} 的身份下执行：{@link GlobalSettingReader}
     * 按当前用户读覆盖，所以 embedding 引擎 / 模型 / 维度、切片、上传目录等
     * 取到的都是**这个账号自己的**配置。</p>
     *
     * @param userId 账号标识；为 {@code null} 表示无用户上下文（健康检查等），
     *               此时读全局配置，落在系统桶目录
     * @return 构建的结果
     */
    private RagClient build(String userId) {
        if (!StringUtils.hasText(userId)) {
            return doBuild(null);
        }
        var resume = new UserResume().setUserId(userId);
        var holder = new RagClient[1];
        AiUserContext.runWith(resume, () -> holder[0] = doBuild(userId));
        return holder[0];
    }

    /**
     * 在当前线程身份下真正执行构建（供 {@link #build(String)} 包装身份后调用）。
     *
     * @param userId 账号标识，允许为 {@code null}
     * @return 构建的结果
     */
    private RagClient doBuild(String userId) {
        // 引擎不再读取 rag_embedding_model（那是实现细节，界面上也不该让人选）；
        // 改为由「所选 Embedding 模型」反推，且不允许回落任何默认引擎。
        String ragEmbeddingModelId = settingReader.getStr(GROUP, "rag_embedding_model_name");
        String ragEmbeddingModel = resolveEmbeddingEngineByModel(ragEmbeddingModelId);
        if (!StringUtils.hasText(ragEmbeddingModelId)) {
            log.warn("[RagClientManager] 账号 {} 未配置 Embedding 模型，RAG 检索不可用"
                    + "（请到「设置中心 → 默认模型 → RAG 知识库」选择一个 Embedding 模型；"
                    + "引擎由所选模型自动推导，无需手动指定）",
                    StringUtils.defaultString(userId, SYSTEM_ACCOUNT));
        } else if (!StringUtils.hasText(ragEmbeddingModel)) {
            log.warn("[RagClientManager] 账号 {} 配置的 Embedding 模型 '{}' 既不在本地模型库、"
                            + "其供应商也未提供 embedding 能力，无法确定引擎，RAG 检索不可用",
                    StringUtils.defaultString(userId, SYSTEM_ACCOUNT), ragEmbeddingModelId);
        }
        String ragLlmModel = settingReader.getStr(GROUP, "rag_llm_model");
        // 兜底：知识库未单独配置模型时，回落「默认模型」——
        // LLM 用默认对话模型；embedding 用默认模型所属供应商作为 embedding 引擎
        // （该供应商未注册 EmbeddingClient SPI 时仍按原逻辑降级为占位实现并 WARN）。
        if (!StringUtils.hasText(ragLlmModel)) {
            ragLlmModel = settingReader.getStr("runtime", "default_model");
            if (StringUtils.hasText(ragLlmModel)) {
                log.info("[RagClientManager] rag_llm_model 未配置，回落默认模型: {}", ragLlmModel);
            }
        }
        String ragUploadDir = settingReader.getStr(GROUP, "rag_upload_dir");
        String ragVectorStorage = settingReader.getStr(GROUP, "rag_vector_storage");
        String ragSystemPrompt = settingReader.getStr(GROUP, "rag_system_prompt");
        String ragSplitter = settingReader.getStr(GROUP, "rag_splitter");
        int chunkSize = settingReader.getInt(GROUP, "rag_chunk_size", properties.getChunkSize());
        int chunkOverlap = settingReader.getInt(GROUP, "rag_chunk_overlap", properties.getChunkOverlap());

        // 检索 Top-K 与相似度阈值：优先读取全局设置，未配置时回退配置属性
        int topK = settingReader.getInt(GROUP, "rag_default_top_k", properties.getDefaultTopK());
        double similarityThreshold = settingReader.getDouble(GROUP, "rag_similarity_threshold",
                properties.getDefaultSimilarityThreshold());
        int embeddingDimensions = settingReader.getInt(GROUP, "rag_embedding_dimensions",
                DEFAULT_EMBEDDING_DIMENSIONS);

        // 按用户解析模型：不在建库时固定一个全局模型，改为「每次生成时解析当前用户」，
        // 让「设置中心 → 默认模型」里用户自己的 RAG LLM / 默认模型真正生效。
        ChatClient chatClient = new UserRagChatClient(userId);

        // 上传目录按账号分桶：分块内容缓存（chunk-content-cache.tsv）落在 uploadDir 下，
        // 共用一个目录会让不同账号的分块原文互相覆盖，检索结果串到别人文档上。
        String baseUploadDir = StringUtils.hasText(ragUploadDir) ? ragUploadDir : properties.getUploadDir();
        String uploadDir = accountUploadDir(baseUploadDir, userId);

        EmbeddingClient embeddingClient = createEmbeddingClient(ragEmbeddingModel, embeddingDimensions);
        VectorStorage vectorStorage = createVectorStorage(ragVectorStorage, uploadDir, embeddingDimensions, userId);
        if (vectorStorage == null) {
            log.error("[RagClientManager] 向量存储不可用，RAG 客户端不构建。"
                    + "请到 RAG 设置里选择一个已注册的向量库并检查其配置");
            return null;
        }
        TextSplitter splitter = createTextSplitter(ragSplitter, chunkSize, chunkOverlap);
        RagClientSetting setting = RagClientSetting.builder()
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .uploadDir(uploadDir)
                .chatClient(chatClient)
                .embeddingClient(embeddingClient)
                .textSplitter(splitter)
                .vectorStorage(vectorStorage)
                .systemPrompt(ragSystemPrompt)
                .textExtractor(resolveTextExtractor())
                .build();

        RagClient client = createClient(setting);
        if (client != null) {
            log.info("[RagClientManager] RAG 客户端构建完成: account={}, embedding={}, storage={}, uploadDir={}, splitter={}, chunkSize={}, overlap={}",
                    StringUtils.defaultString(userId, SYSTEM_ACCOUNT),
                    StringUtils.defaultString(ragEmbeddingModel, "memory"),
                    StringUtils.defaultString(ragVectorStorage, properties.getVectorStorage()),
                    uploadDir, StringUtils.defaultString(ragSplitter, "sentence"), chunkSize, chunkOverlap);
        } else {
            log.error("[RagClientManager] 无可用 RagClient SPI 实现，RAG 功能不可用");
        }
        return client;
    }

    /**
     * 解析 RAG 使用的 LLM：
     * 1. 按 rag_llm_model 从 ChatClientRegistry 查找已注册客户端
     * 2. 模型引用形如 供应商ID:模型名 时，直接用 sys_ai_provider 的 base_url + API Key 构建
     * 3. 查不到则用 registry 默认客户端
     * 4. 都没有时，从 runtime 全局设置（provider/apiKey/baseUrl/model）直接构造真实客户端
     * @param ragLlmModel ragLlm模型，不允许为 null
     * @return Chat客户端 对象
     */
    private ChatClient resolveChatClient(String ragLlmModel) {
        // 1. 指定了 LLM 模型：先精确查注册中心，再按「供应商ID:模型名」从供应商表解析真实凭据。
        //    必须优先于「注册中心默认」——默认可能是聚合客户端，早先它内部混着演示用的
        //    内存客户端且排在前面，永远「成功」返回固定文案，真实模型根本没机会被调用
        //    （表现为 /ask 静默返回「【内存模式】…」假答案）。
        if (StringUtils.hasText(ragLlmModel)) {
            if (chatClientRegistry != null) {
                ChatClient byModel = chatClientRegistry.getByModel(ragLlmModel);
                if (isRealChatClient(byModel)) {
                    return byModel;
                }
            }
            ChatClient byProvider = buildProviderChatClient(ragLlmModel);
            if (isRealChatClient(byProvider)) {
                return byProvider;
            }
            log.warn("[RagClientManager] RAG LLM 模型 {} 未能解析为真实对话客户端", ragLlmModel);
        }
        // 2. 注册中心默认客户端（装配时已剔除内存演示实现）
        if (chatClientRegistry != null) {
            ChatClient defaultClient = chatClientRegistry.getDefault();
            if (isRealChatClient(defaultClient)) {
                return defaultClient;
            }
        }
        // 3. runtime 全局设置直接构建
        ChatClient runtime = buildRuntimeChatClient();
        if (isRealChatClient(runtime)) {
            return runtime;
        }
        // 4. 都没有 → 返回 null，让 RAG 明确不可用；绝不回落到内存模拟客户端
        log.warn("[RagClientManager] 未解析到真实对话客户端，RAG 不可用（请配置 RAG LLM 模型）");
        return null;
    }

    /**
     * 判断是否为可用的真实对话客户端（排除演示用的内存实现）。
     *
     * @param client 待判定的客户端
     * @return true 表示可用于真实生成
     */
    private boolean isRealChatClient(ChatClient client) {
        return client != null && !(client instanceof MemoryChatClient);
    }

    /**
     * 把「裸模型名」定位到供应商并按供应商凭据构建 ChatClient。
     *
     * <p>模型引用形如 {@code 供应商ID:模型名} 时由 {@link #buildProviderChatClient} 直接构建；
     * 而默认模型链（{@link EffectiveChatModelResolver}）返回的是<b>裸模型名</b>，
     * 需要先查 {@code sys_ai_provider_model} 找到 providerId 再构建。</p>
     *
     * @param modelName 裸模型名
     * @return 对话客户端；查不到或构建失败时返回 空
     */
    private ChatClient buildChatClientByModelName(String modelName) {
        if (StringUtils.isBlank(modelName)) {
            return null;
        }
        try {
            SysAiProviderModel model = providerModelMapper.selectOne(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getModel, modelName)
                            .eq(SysAiProviderModel::getEnabled, true)
                            .last("limit 1"));
            if (model == null || StringUtils.isBlank(model.getProviderId())) {
                return null;
            }
            return buildProviderChatClient(model.getProviderId() + ":" + modelName);
        } catch (Exception e) {
            log.warn("[RagClientManager] 按模型名定位供应商失败 model={}: {}", modelName, e.getMessage());
            return null;
        }
    }

    /**
     * 「按账号解析模型」的对话客户端代理。
     *
     * <p>RAG 管线在生成阶段调用 {@link #chatSync(String)}。这里每次调用都重新解析模型：
     * 该账号自己的 {@code rag/rag_llm_model} → 该账号默认模型 → Bot → 全局默认
     * （后三者由 {@link EffectiveChatModelResolver} 负责）。</p>
     *
     * <p>解析时必须切到本代理<b>绑定的账号</b>身份下（{@link #account}），而不是直接读线程上下文：
     * 管线可能被后台线程、{@code @PreDestroy} 收尾或 WebSocket 通道调用，那些线程没有请求身份，
     * 直接读会退化成全局配置，用错模型。绑定后即使在无身份线程上也解析到该账号自己的模型。</p>
     *
     * <p>解析到的模型对应的 {@link ChatClient} 按「模型引用」缓存，避免每次调用重复建客户端。</p>
     */
    private final class UserRagChatClient implements ChatClient {

        /**
         * 本代理绑定的账号标识；{@code null} 表示系统桶（读全局配置）。
         */
        private final String account;

        /**
         * 模型引用 → 已构建的对话客户端
         */
        private final Map<String, ChatClient> cache = new java.util.concurrent.ConcurrentHashMap<>();

        /**
         * 构造按账号解析的对话客户端代理。
         *
         * @param account 账号标识，允许为 {@code null}（系统桶）
         */
        private UserRagChatClient(String account) {
            this.account = account;
        }

        @Override
        public String chatSync(String prompt) {
            return delegate().chatSync(prompt);
        }

        /**
         * 解析该账号应使用的对话客户端。
         *
         * @return 对话客户端
         */
        private ChatClient delegate() {
            if (!StringUtils.hasText(account)) {
                return delegateAsSystem();
            }
            // 切到本代理绑定的账号身份再解析：settingReader / 解析链都按当前用户读覆盖
            var resume = new UserResume().setUserId(account);
            var holder = new ChatClient[1];
            AiUserContext.runWith(resume, () -> holder[0] = resolveDelegate());
            return holder[0];
        }

        /**
         * 系统桶（无账号身份）的模型解析：直接用当前线程身份读全局配置。
         *
         * @return 对话客户端
         */
        private ChatClient delegateAsSystem() {
            return resolveDelegate();
        }

        /**
         * 在当前线程身份下解析「用户 RAG LLM → 默认模型 → Bot → 全局默认」并构建客户端。
         *
         * @return 对话客户端
         */
        private ChatClient resolveDelegate() {
            // ① 当前用户自己的 RAG LLM（GlobalSettingReader 已实现「用户覆盖优先、全局兜底」）
            String model = settingReader.getStr(GROUP, "rag_llm_model");
            // ② 兜底：当前用户默认模型 → Bot → 全局默认
            if (!StringUtils.hasText(model)) {
                model = effectiveChatModelResolver.resolve(null);
            }
            if (!StringUtils.hasText(model)) {
                throw new IllegalStateException(
                        "未配置可用的对话模型：请在「设置中心 → 默认模型」选择 RAG LLM 或默认模型");
            }
            log.info("[RagClientManager] RAG 生成按用户解析到模型: account={}, model={}",
                    StringUtils.defaultString(account, SYSTEM_ACCOUNT), model);
            ChatClient client = cache.get(model);
            if (client != null) {
                return client;
            }
            // 形如 供应商ID:模型名 直接构建；裸模型名先定位供应商
            client = buildProviderChatClient(model);
            if (client == null) {
                client = buildChatClientByModelName(model);
            }
            if (client == null && chatClientRegistry != null) {
                client = chatClientRegistry.getByModel(model);
            }
            if (!isRealChatClient(client)) {
                throw new IllegalStateException("模型 " + model
                        + " 无法构建对话客户端：请在「模型管理」确认该模型的供应商与密钥");
            }
            cache.put(model, client);
            return client;
        }
    }

    /**
     * 按「供应商ID:模型名」引用从 {@code sys_ai_provider} 构建 ChatClient。
     *
     * <p>模型引用支持两种形态：{@code 供应商ID:模型名} 与裸模型名。凭据取自供应商表的
     * {@code credentials} JSON，同时兼容两种键形态：{@code api_keys}（列表，取第一个非空）
     * 与 {@code api_key}（单值）。因此不会退化到 {@code runtime} 分组的
     * {@code openai}/{@code gpt-4o} 首次种子默认值。</p>
     *
     * @param modelRef 模型引用，允许为 空
     * @return Chat客户端 对象；引用为空、供应商缺失或凭据不完整时返回 空
     */
    private ChatClient buildProviderChatClient(String modelRef) {
        if (StringUtils.isBlank(modelRef)) {
            return null;
        }
        String providerId = null;
        String modelName = modelRef.trim();
        int idx = modelName.indexOf(':');
        if (idx > 0) {
            providerId = modelName.substring(0, idx).trim();
            modelName = modelName.substring(idx + 1).trim();
        }
        if (StringUtils.isBlank(modelName)) {
            return null;
        }
        try {
            SysAiProvider provider = providerId != null
                    ? providerMapper.selectById(providerId)
                    : null;
            if (provider == null) {
                log.warn("[RagClientManager] 模型引用 {} 未定位到供应商", modelRef);
                return null;
            }
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                log.warn("[RagClientManager] 供应商 {} 已禁用，无法构建 ChatClient", provider.getName());
                return null;
            }
            List<String> apiKeys = extractApiKeys(provider.getCredentials());
            if (apiKeys.isEmpty()) {
                log.warn("[RagClientManager] 供应商 {} 未配置 API Key，无法构建 ChatClient",
                        provider.getName());
                return null;
            }
            String platform = StringUtils.isBlank(provider.getPlatform())
                    ? "openai" : provider.getPlatform().trim();
            String baseUrl = StringUtils.hasText(provider.getBaseUrl())
                    ? provider.getBaseUrl().trim() : null;
            String authScheme = StringUtils.hasText(provider.getAuthScheme())
                    ? provider.getAuthScheme().trim() : null;
            Map<String, RouteTarget> channelRoutes = modelClientResolver.mergedRoutes(
                    provider, ModelClientResolver.ownerScopeOf(provider));
            List<ChatClient> keyClients = new ArrayList<>();
            for (String apiKey : apiKeys) {
                ChatClientSetting setting = ChatClientSetting.builder()
                        .provider(platform)
                        .appKey(apiKey)
                        .baseUrl(baseUrl)
                        .model(modelName)
                        .authScheme(authScheme)
                        .routes(channelRoutes)
                        .build();
                ChatClient c = ChatClient.create(setting);
                if (c != null) {
                    keyClients.add(c);
                }
            }
            if (keyClients.isEmpty()) {
                return null;
            }
            if (keyClients.size() == 1) {
                log.info("[RagClientManager] 按供应商表创建真实 ChatClient: provider={}, model={}",
                        provider.getName(), modelName);
                return keyClients.get(0);
            }
            // 多 Key failover：单个 Key 被上游限流(429)或失败时自动切到下一个。
            // 否则「供应商配了多个 Key 却只用第一个」会在该 Key 限流时直接让 /ask 失败。
            AggregateChatClientSetting aggregateSetting = new AggregateChatClientSetting();
            aggregateSetting.setStrategy("failover");
            AggregateChatClient aggregate = new AggregateChatClient(aggregateSetting);
            for (int i = 0; i < keyClients.size(); i++) {
                aggregate.addClient("key-" + i, null, 1, keyClients.get(i));
            }
            log.info("[RagClientManager] 按供应商表创建 {}-Key failover ChatClient 组: provider={}, model={}",
                    keyClients.size(), provider.getName(), modelName);
            return aggregate;
        } catch (Exception e) {
            log.warn("[RagClientManager] 供应商表 ChatClient 创建失败({}): {}", modelRef, e.getMessage());
            return null;
        }
    }

    /**
     * 从供应商 credentials 中提取全部可用 API Key（用于多 Key failover）。
     *
     * <p>兼容两种存储形态：{@code api_keys} 为字符串列表（多 Key 轮换），
     * {@code api_key} 为单值；按出现顺序去重返回。</p>
     *
     * @param credentials 凭证 JSON，允许为 空
     * @return API Key 列表；不存在时返回空列表
     */
    private List<String> extractApiKeys(Map<String, Object> credentials) {
        List<String> keys = new ArrayList<>();
        if (credentials == null || credentials.isEmpty()) {
            return keys;
        }
        Object multi = credentials.get("api_keys");
        if (multi instanceof List<?> list) {
            for (Object item : list) {
                String key = item == null ? null : String.valueOf(item).trim();
                if (StringUtils.hasText(key) && !keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        Object single = credentials.get("api_key");
        if (single != null) {
            String key = String.valueOf(single).trim();
            if (StringUtils.hasText(key) && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * 从 runtime 全局设置构建真实 ChatClient。
     * 读取 provider/provider_api_key/provider_base_url/default_model，构造并返回。
     * @return Chat客户端 对象
     */
    private ChatClient buildRuntimeChatClient() {
        String provider = settingReader.getStr("runtime", "provider");
        String apiKey = settingReader.getStr("runtime", "provider_api_key");
        String baseUrl = settingReader.getStr("runtime", "provider_base_url");
        String model = settingReader.getStr("runtime", "default_model");
        if (StringUtils.isBlank(provider) || StringUtils.isBlank(apiKey)) {
            log.warn("[RagClientManager] runtime 分组未配置 provider/api_key，无法构建真实 ChatClient");
            return null;
        }
        try {
            ChatClientSetting setting = ChatClientSetting.builder()
                    .provider(provider.trim())
                    .appKey(apiKey.trim())
                    .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl.trim() : null)
                    .model(StringUtils.hasText(model) ? model.trim() : null)
                    // 认证方案：runtime.provider_auth_scheme / provider_auth_header / provider_auth_prefix
                    // 用于 Dots Studio 这类只认 api-key 头、不认 Authorization 的服务商
                    .authScheme(settingReader.getStr("runtime", "provider_auth_scheme"))
                    .authHeader(settingReader.getStr("runtime", "provider_auth_header"))
                    .authPrefix(settingReader.getStr("runtime", "provider_auth_prefix"))
                    .build();
            ChatClient client = ChatClient.create(setting);
            if (client != null) {
                log.info("[RagClientManager] 从 runtime 配置创建真实 ChatClient: provider={}, model={}", provider, model);
            }
            return client;
        } catch (Exception e) {
            log.warn("[RagClientManager] runtime ChatClient 创建失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析文本提取器：直接复用共享的 OCR 管线（ocr 分组为单一事实来源）。
     *
     * <p>OCR 是否启用由 {@link OcrClientManager} 依 ocr 分组判定；未配置时不做图片 OCR
     * （上传图片按 UTF-8 降级）。管线配置热更新由 OcrClientManager 负责，这里在提取时
     * 实时取当前管线，因此 OCR 配置改动无需重建 RAG 客户端。</p>
     *
     * @return 文本提取器；OCR 未配置时返回 空
     */
    private TextExtractor resolveTextExtractor() {
        if (!ocrClientManager.available()) {
            return null;
        }
        return new OcrPipelineTextExtractor(ocrClientManager);
    }

    /**
     * 安全解析 float，失败返回默认值。
     * @param s 待解析字符串，null 或空白时直接返回默认值
     * @param def 解析失败时的默认值
     * @return 解析出的 float；解析失败或输入为空时返回 def
     */
    private static float parseFloatSafe(String s, float def) {
        if (!StringUtils.hasText(s)) {
            return def;
        }
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 由「所选 Embedding 模型」反推该用哪个 embedding 引擎（SPI provider）。
     *
     * <p>用户只该选模型，不该被要求理解「onnx / openai / siliconflow」这些引擎名——
     * 引擎是实现细节，由模型本身决定：</p>
     * <ol>
     *   <li>模型 id 命中本地 {@code ModelRegistry} → 引擎是 {@code onnx}（本地推理，免凭证）；</li>
     *   <li>否则按模型名查 {@code sys_ai_provider_model} → 取其供应商的 platform，
     *       仅当该 platform 在 EmbeddingClient SPI 里注册过才采用；</li>
     *   <li>两者都不命中 → 返回 {@code null}，<b>不回落任何默认引擎</b>。</li>
     * </ol>
     *
     * <p>第3 条是刻意的：早先「未配置就回落 provider / 占位实现」会让 RAG 带着
     * 从未有人配置的种子值（实测 {@code rag_embedding_model=onnx}、种子时间
     * 2026-09-30）悄悄跑起来，用户看到的是「已配置」。未配置就明确报未配置。</p>
     *
     * @param modelId 用户选择的 Embedding 模型标识，允许为 {@code null}
     * @return 引擎名；无法判定时返回 {@code null}
     */
    /**
     * 把「界面保存的模型引用」收敛成裸模型名。
     *
     * <p>设置页保存的值形如 {@code onnx:bge-large-zh-embedding}、{@code agnes:xxx}，
     * 而 {@code ModelRegistry} 与 {@code sys_ai_provider_model} 存的都是裸名；
     * 底层 embedding 实现同样只认裸名——实测带前缀直接传下去会抛
     * 「模型未注册 onnx:bge-large-zh-embedding」。</p>
     *
     * @param modelRef 模型引用，允许为 {@code null}
     * @return 裸模型名；入参为空时返回空串
     */
    private static String bareModelId(String modelRef) {
        if (!StringUtils.hasText(modelRef)) {
            return "";
        }
        String ref = modelRef.trim();
        int colon = ref.indexOf(':');
        if (colon > 0 && colon < ref.length() - 1) {
            return ref.substring(colon + 1).trim();
        }
        return ref;
    }

    private String resolveEmbeddingEngineByModel(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        String bareModel = bareModelId(modelId);
        if (bareModel.isEmpty()) {
            return null;
        }
        try {
            // ① 本地模型：ModelRegistry 里有就是 onnx 本地推理
            ModelRegistry.discoverAll();
            for (ModelRegistry.Entry entry : ModelRegistry.getAll()) {
                if (entry != null && bareModel.equalsIgnoreCase(entry.modelId())) {
                    return "onnx";
                }
            }
        } catch (Exception e) {
            log.warn("[RagClientManager] 枚举本地模型失败，无法判断 {} 是否本地模型: {}", bareModel, e.getMessage());
        }
        // ② 云端模型：按模型名定位供应商，取其 platform 作为引擎
        try {
            SysAiProviderModel row = providerModelMapper.selectOne(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getModel, bareModel)
                            .eq(SysAiProviderModel::getEnabled, true)
                            .last("limit 1"));
            if (row == null || StringUtils.isBlank(row.getProviderId())) {
                return null;
            }
            SysAiProvider provider = providerMapper.selectById(row.getProviderId());
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
                return null;
            }
            String platform = StringUtils.isBlank(provider.getPlatform()) ? null : provider.getPlatform().trim();
            if (platform == null) {
                return null;
            }
            // 只有真的注册了对应 EmbeddingClient SPI 才算可用引擎，避免选了个跑不了的
            for (String registered : ServiceProvider.of(EmbeddingClient.class).getExtensions()) {
                if (registered != null && registered.equalsIgnoreCase(platform)) {
                    return platform;
                }
            }
            log.warn("[RagClientManager] 供应商 {} 的 platform '{}' 没有注册 EmbeddingClient SPI，无法作为引擎",
                    provider.getName(), platform);
        } catch (Exception e) {
            log.warn("[RagClientManager] 按模型 {} 反查引擎失败: {}", bareModel, e.getMessage());
        }
        return null;
    }

    /**
     * 创建向量化客户端。
     * 1. 按 rag_embedding_model（如 "openai"）从 SPI 查找 provider
     * 2. 若为外部 provider（非 memory），从 rag/runtime 分组读取 apiKey/baseUrl/model 构造真实客户端
     * 3. 找不到或构造失败时降级为离线 MemoryEmbeddingClient
     *
     * @param model               嵌入引擎名称（如 "openai" / "siliconflow" / "memory"，留空取属性默认）
     * @param embeddingDimensions 向量维度，透传给真实嵌入客户端保证输出维度一致
     * @return EmbeddingClient 实例（失败时降级为离线内存实现）
     */
    private EmbeddingClient createEmbeddingClient(String model, int embeddingDimensions) {
        String engine = StringUtils.hasText(model) ? model.trim() : properties.getEmbeddingEngine();
        if (StringUtils.isBlank(engine)) {
            engine = properties.getEmbeddingEngine();
        }
        if ("memory".equalsIgnoreCase(engine)) {
            logPlaceholderEmbedding(engine, embeddingDimensions);
            return new MemoryEmbeddingClient().dimensions(embeddingDimensions);
        }
        try {
            Set<String> providers = ServiceProvider.of(EmbeddingClient.class).getExtensions();
            // SPI 别名在 ServiceDefinition 中统一大写归一化（实测 getExtensions() 返回
            // ONNX / MEMORY / JVECTOR 等全大写名），这里必须按同一规则比较，
            // 否则 contains(lowerCase) 恒为 false，所有真实 embedding 引擎都会被误判为
            // 「未注册」并静默降级成占位向量。
            boolean registered = false;
            for (String provider : providers) {
                if (provider != null && provider.equalsIgnoreCase(engine)) {
                    registered = true;
                    break;
                }
            }
            if (!registered) {
                // 降级到占位实现会让语义检索恒定 0 命中，必须让运维看见
                log.warn("[RagClientManager] EmbeddingClient SPI 中没有 provider '{}'（已注册: {}），"
                        + "回退占位实现，语义检索不可用", engine, providers);
                return new MemoryEmbeddingClient().dimensions(embeddingDimensions);
            }
            // 优先 rag 分组独立 embedding 配置，其次 runtime 配置
            String embKey = settingReader.getStr(GROUP, "rag_embedding_api_key");
            String embBase = settingReader.getStr(GROUP, "rag_embedding_base_url");
            // 界面保存的是「onnx:模型名」，底层实现只认裸名，必须剥掉前缀，
            // 否则会抛「模型未注册 onnx:xxx」。
            String embModel = bareModelId(settingReader.getStr(GROUP, "rag_embedding_model_name"));
            boolean local = LOCAL_EMBEDDING_ENGINES.contains(engine.toLowerCase());
            if (!local) {
                if (!StringUtils.hasText(embKey)) {
                    embKey = settingReader.getStr("runtime", "provider_api_key");
                }
                if (!StringUtils.hasText(embBase)) {
                    embBase = settingReader.getStr("runtime", "provider_base_url");
                }
            }
            // 在线引擎必须有 apiKey + baseUrl；本地 ONNX 推理不需要任何凭据，
            // 若沿用同一门槛会导致「已注册且模型就位」也被判为不可用而静默降级。
            boolean credentialReady = local
                    || (StringUtils.hasText(embKey) && StringUtils.isNotBlank(embBase));
            if (credentialReady) {
                EmbeddingClientSetting setting = EmbeddingClientSetting.builder()
                        .provider(engine.toLowerCase())
                        .appKey(StringUtils.hasText(embKey) ? embKey.trim() : null)
                        .baseUrl(StringUtils.hasText(embBase) ? embBase.trim() : null)
                        .model(StringUtils.hasText(embModel) ? embModel.trim() : null)
                        .dimensions(embeddingDimensions)
                        .build();
                EmbeddingClient client = EmbeddingClient.create(setting);
                if (client != null) {
                    log.info("[RagClientManager] 创建真实 EmbeddingClient: provider={}, model={}, dim={}, 本地推理={}",
                            engine, embModel, embeddingDimensions, local);
                    return client;
                }
            }
            // 无 runtime key 时回退离线
            log.warn("[RagClientManager] 无可用 embedding apiKey/baseUrl，EmbeddingClient 回退占位实现，语义检索不可用（engine={}）", engine);
        } catch (Exception e) {
            log.warn("[RagClientManager] EmbeddingClient SPI 创建失败，回退占位实现，语义检索不可用: {}", e.getMessage());
        }
        logPlaceholderEmbedding(engine, embeddingDimensions);
        return new MemoryEmbeddingClient().dimensions(embeddingDimensions);
    }

    /**
     * 记录占位向量化实现的固有限制。
     *
     * <p>{@link MemoryEmbeddingClient} 生成的是无语义伪向量，任意两条不同文本的余弦
     * 实测恒在 0.63~0.69，而 {@code rag_similarity_threshold} 默认 0.7 恰好压在其上，
     * 因此语义检索会稳定返回 0 命中、{@code sources} 恒空。这既不是检索链路的缺陷，
     * 也不能靠调阈值解决（调低会让任意文本互相召回，等于注入无关上下文），
     * 唯一出路是配置真实 embedding 服务。此处按 WARN 记录，避免运维只看到「0 命中」而无线索。</p>
     *
     * @param engine                 引擎名（配置值）
     * @param embeddingDimensions    向量维度
     */
    private void logPlaceholderEmbedding(String engine, int embeddingDimensions) {
        log.warn("[RagClientManager] 使用占位向量化 MemoryEmbeddingClient（engine={}, dim={}）："
                        + "该实现无语义能力，任意两条不同文本余弦恒在 0.63~0.69，与默认相似度阈值 0.7 重叠，"
                        + "语义检索将恒定 0 命中；需要真实召回请在「设置中心 → 默认模型 → RAG」选择 Embedding 模型（引擎由所选模型自动推导）",
                engine, embeddingDimensions);
    }

    /**
     * 按名称动态创建向量存储：配置来自业务侧持久化，实现由 SPI 自己决定。
     *
     * <p>全程没有按名称的硬编码分支，也没有静默降级：
     * 未注册的实现、需要数据源却没容器、或 SPI 自身抛错，都会明确失败并返回 {@code null}，
     * 由调用方让 RAG 不可用——而不是悄悄把知识库索引写进内存里假装成功。</p>
     *
     * <p>业务默认值只在用户尚未保存配置时生效（当前仅 jvector 的索引落盘位置），
     * 一旦用户在页面保存过，就完全以保存值为准。</p>
     *
     * @param storageName        存储名，取自已注册的 VectorStorageProvider SPI
     * @param uploadDir          RAG 上传目录，jvector 默认索引目录的父目录
     * @param embeddingDimensions 向量维度
     * @return VectorStorage 实例；创建失败返回 null
     */
    private VectorStorage createVectorStorage(String storageName, String uploadDir, int embeddingDimensions,
                String userId) {
        String name = StringUtils.hasText(storageName)
                ? storageName.trim().toLowerCase()
                : properties.getVectorStorage();
        // 兜底必须是 jvector：默认落到 memory 会让向量随进程重启全部丢失，
        // 且没有任何提示，属静默降级。jvector 未注册时下方 createDynamic 会明确抛错。
        name = StringUtils.hasText(name) ? name.trim().toLowerCase() : "jvector";
        VectorCompareAlgorithm algorithm = VectorCompareAlgorithm.cosine();
        int dim = embeddingDimensions > 0 ? embeddingDimensions : properties.getEmbeddingDimensions();
        try {
            Map<String, Object> config = resolveVectorStorageConfig(name, uploadDir);
            applyPerUserIsolation(name, config, uploadDir, userId);
            VectorStorage storage = VectorStorageProvider.createDynamic(
                    name, dim, algorithm, config, resolveDataSource());
            if (storage != null) {
                log.info("[RagClientManager] 向量存储已就绪: name={}, account={}, dim={}, 字段={}, indexPath={}",
                        name, StringUtils.defaultString(userId, SYSTEM_ACCOUNT), dim, config.keySet(),
                        config.get(JVectorVectorStorageProvider.KEY_INDEX_PATH));
            }
            return storage;
        } catch (Throwable e) {
            log.error("[RagClientManager] 向量存储 [{}] 初始化失败，RAG 不可用（已注册: {}）: {}",
                    name, VectorStorageProvider.providers(), e.getMessage());
            return null;
        }
    }

    /**
     * 把向量库的落盘位置改写成**按账号分开**的位置。
     *
     * <p>必须覆盖而不是只在缺失时补默认值：{@code rag_vector_storage_config} 里保存的
     * 落盘位置（线上实测 jvector 的 {@code indexPath=/opt/ai-starter/workspace/rag/jvector-index}）
     * 是<b>全局同一份</b>索引。不覆盖的话所有账号会共用一个索引：
     * 一是换 embedding 维度即互相破坏（人脸那边已实测过，维度 256→512 直接抛 search 失败），
     * 二是 A 账号的文档能被 B 账号检索出来。</p>
     *
     * <p>按向量库描述器（{@link VectorStorageDescriptor}）声明的字段判定落盘位置，
     * 不硬编码库名：声明 {@code indexPath} 的（jvector）改成账号内路径；
     * 声明 {@code tableName} / {@code collection} 的（mysql、postgresql、milvus）
     * 改成带账号后缀的表名/集合名——这些实现都是 {@code CREATE TABLE IF NOT EXISTS}，
     * 首次写入时自建，无需预建。都不声明的库无从隔离，只能明确 WARN，
     * 不能让它静默变成共享索引。</p>
     *
     * <p>jvector <b>不会自动创建缺失的父目录</b>（写入静默失败，表现为「入库成功但重启即丢」），
     * 因此文件型落盘位置的建目录动作必须由业务侧做。</p>
     *
     * @param name      存储名
     * @param config    待改写的配置（原地修改）
     * @param uploadDir 已按账号分桶的上传目录
     * @param userId    账号标识，允许为 {@code null}（系统桶）
     */
    private void applyPerUserIsolation(String name, Map<String, Object> config, String uploadDir, String userId) {
        var keys = declaredFieldKeys(name);
        if (keys == null) {
            // 取不到描述器属于基础设施故障，不是「这个库没有可隔离字段」。
            // 两者必须分开报：混在一起会让人以为「隔离字段判定」本身是可靠的，
            // 而这里实际是判定失效后放行了共享落盘位置。
            log.error("[RagClientManager] 无法读取向量库 [{}] 的描述器，跳过按账号隔离判定，"
                            + "账号 {} 的向量将与其它账号共用同一处落盘位置（换 embedding 维度会互相破坏、"
                            + "跨账号可检索到对方分块）。请检查 VectorStorage SPI 注册",
                    name, StringUtils.defaultString(userId, SYSTEM_ACCOUNT));
            return;
        }
        if (keys.contains(JVectorVectorStorageProvider.KEY_INDEX_PATH)) {
            isolateFileIndex(config, uploadDir, userId, JVectorVectorStorageProvider.KEY_INDEX_PATH);
            return;
        }
        for (String namespaceKey : NAMESPACE_FIELD_KEYS) {
            if (keys.contains(namespaceKey)) {
                isolateNamedNamespace(config, namespaceKey, userId);
                return;
            }
        }
        log.warn("[RagClientManager] 向量库 [{}] 未声明可隔离的落盘字段（已声明: {}），"
                        + "账号 {} 的向量将与其它账号共用同一处落盘位置——换 embedding 维度会互相破坏，"
                        + "且跨账号可检索到对方分块",
                name, keys, StringUtils.defaultString(userId, SYSTEM_ACCOUNT));
    }

    /**
     * 取出某向量库描述器里声明的字段键集合。
     *
     * @param name 存储名
     * @return 字段键集合；<b>描述器整体不可用时返回 {@code null}</b>，
     *         与「描述器可用但该库没声明隔离字段」的空集合区分开
     */
    private Set<String> declaredFieldKeys(String name) {
        List<VectorStorageDescriptor> descriptors;
        try {
            descriptors = VectorStorageProvider.descriptors();
        } catch (Exception e) {
            log.warn("[RagClientManager] 读取向量库 [{}] 描述器失败: {}", name, e.getMessage());
            return null;
        }
        if (descriptors == null) {
            return null;
        }
        for (VectorStorageDescriptor descriptor : descriptors) {
            if (descriptor != null && name.equalsIgnoreCase(descriptor.name())
                    && descriptor.fields() != null) {
                var keys = new LinkedHashSet<String>();
                for (VectorStorageField field : descriptor.fields()) {
                    if (field != null && StringUtils.hasText(field.key())) {
                        keys.add(field.key());
                    }
                }
                return keys;
            }
        }
        return null;
    }

    /**
     * 文件型索引（jvector）改写为账号内路径，并预先创建父目录。
     *
     * @param config      待改写的配置（原地修改）
     * @param uploadDir   已按账号分桶的上传目录
     * @param userId      账号标识，允许为 {@code null}
     * @param pathKey     路径字段键
     */
    private void isolateFileIndex(Map<String, Object> config, String uploadDir, String userId, String pathKey) {
        var indexPath = Path.of(uploadDir).resolve(INDEX_NAME).toAbsolutePath().normalize();
        try {
            Files.createDirectories(indexPath.getParent());
        } catch (Exception e) {
            log.warn("[RagClientManager] 账号 {} 的向量索引目录创建失败，索引可能无法落盘: {} -> {}",
                    StringUtils.defaultString(userId, SYSTEM_ACCOUNT), indexPath.getParent(), e.getMessage());
        }
        Object saved = config.get(pathKey);
        config.put(pathKey, indexPath.toString());
        if (saved != null && !String.valueOf(saved).equals(indexPath.toString())) {
            log.info("[RagClientManager] 账号 {} 的索引路径由全局共享改为按账号隔离: {} -> {}",
                    StringUtils.defaultString(userId, SYSTEM_ACCOUNT), saved, indexPath);
        }
    }

    /**
     * 命名空间型落盘位置（表名 / 集合名）改写为带账号后缀的名称。
     *
     * <p>后缀必须满足 SQL 标识符白名单 {@code [A-Za-z_][A-Za-z0-9_]{0,63}}：
     * 向量库实现会用 {@code checkIdentifier} 校验，非字母数字（含 {@code -} 与 {@code .}）
     * 会被直接判非法并抛异常；总长超 64 也要截断。截断后补一段账号标识的短哈希，
     * 避免两个长账号截成同一个表名而互相串数据。</p>
     *
     * @param config       待改写的配置（原地修改）
     * @param namespaceKey 命名空间字段键（{@code tableName} / {@code collection}）
     * @param userId       账号标识，允许为 {@code null}
     */
    private void isolateNamedNamespace(Map<String, Object> config, String namespaceKey, String userId) {
        Object saved = config.get(namespaceKey);
        String base = saved != null && StringUtils.hasText(String.valueOf(saved))
                ? String.valueOf(saved).trim()
                : DEFAULT_NAMESPACE;
        String namespaced = base + "_" + identifierSuffix(userId);
        config.put(namespaceKey, namespaced);
        if (saved != null && !String.valueOf(saved).equals(namespaced)) {
            log.info("[RagClientManager] 账号 {} 的向量库 {} 由 {} 改为按账号隔离: {} -> {}",
                    StringUtils.defaultString(userId, SYSTEM_ACCOUNT), namespaceKey, saved, namespaced);
        }
    }

    /**
     * 把账号标识收敛成可作 SQL 标识符后缀的片段。
     *
     * @param userId 账号标识，允许为 {@code null}
     * @return 只含字母数字与下划线、长度受控的后缀
     */
    private static String identifierSuffix(String userId) {
        String raw = StringUtils.hasText(userId) ? userId : SYSTEM_ACCOUNT;
        var sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetterOrDigit(c) && c < 128 ? c : '_');
        }
        String cleaned = sb.toString().replaceAll("^_+", "");
        if (cleaned.isBlank()) {
            cleaned = SYSTEM_ACCOUNT;
        }
        // 保留 base 的默认长度预算：DEFAULT_NAMESPACE(13) + '_' + 50 = 64，仍在白名单内
        if (cleaned.length() > MAX_NAMESPACE_SUFFIX) {
            cleaned = cleaned.substring(0, MAX_NAMESPACE_SUFFIX)
                    + "_" + Integer.toHexString(raw.hashCode());
        }
        return cleaned;
    }

    /**
     * 取出指定向量库的保存配置。
     *
     * <p>持久化结构是「SPI 名称 → 键值配置」的一层映射，
     * 这样在不同向量库之间来回切换时各自的配置都还在，不会互相覆盖。</p>
     *
     * @param name      SPI 名称
     * @param uploadDir RAG 上传目录，用于补齐业务默认索引路径
     * @return 键值配置，不可为 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveVectorStorageConfig(String name, String uploadDir) {
        Map<String, Object> all = readVectorStorageConfigs();
        Object raw = all.get(name);
        Map<String, Object> config = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    config.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        applyBusinessDefaults(name, config, uploadDir);
        return config;
    }

    /**
     * 业务默认配置：仅在用户尚未保存该项时补充，不覆盖已保存的值。
     *
     * <p>这里放的是「RAG 索引放在哪」这类业务约定，属于业务侧知识；
     * 具体如何落盘、怎么建索引仍然由 SPI 自己决定。</p>
     *
     * @param name      SPI 名称
     * @param config    待补充的配置（原地修改）
     * @param uploadDir RAG 上传目录
     */
    private void applyBusinessDefaults(String name, Map<String, Object> config, String uploadDir) {
        if ("jvector".equals(name)
                && !config.containsKey(JVectorVectorStorageProvider.KEY_INDEX_PATH)
                && StringUtils.hasText(uploadDir)) {
            config.put(JVectorVectorStorageProvider.KEY_INDEX_PATH,
                    uploadDir + "/" + INDEX_NAME);
        }
    }

    /**
     * 计算某账号的 RAG 上传目录（{@code <基础目录>/<账号>/rag}）。
     *
     * <p>必须按账号分开的原因有两处，都不是洁癖：
     * {@code RagPipeline} 的分块内容缓存固定写在 {@code uploadDir} 下
     * （{@code chunk-content-cache.tsv}），共用目录时 A 账号的分块原文会被 B 覆盖，
     * 检索出来的是别人的正文；向量索引路径也由同一个目录派生（见
     * {@link #applyPerUserIsolation}），换 embedding 维度会互相破坏。</p>
     *
     * @param baseUploadDir 配置的基础目录（{@code rag_upload_dir} 或属性默认值）
     * @param userId        账号标识；为空时用系统桶占位名
     * @return 该账号独占的上传目录
     */
    private String accountUploadDir(String baseUploadDir, String userId) {
        String bucket = StringUtils.hasText(userId) ? sanitize(userId) : SYSTEM_ACCOUNT;
        String base = StringUtils.hasText(baseUploadDir) ? baseUploadDir : ".";
        return base.replace('\\', '/') + "/" + bucket + "/" + ACCOUNT_LEAF;
    }

    /**
     * 把账号标识收敛成安全的单级目录名，避免把写入引出根目录。
     *
     * <p>与 {@code FaceStores#sanitize} 同一套规则：替换路径分隔符与 Windows 保留字符、
     * 折叠 {@code ..}、去掉首尾点与空白。留空时回落到系统桶名，
     * 而不是空串（空串会让路径塌成 {@code base/rag} 这种跨账号共享的位置）。</p>
     *
     * @param account 原始账号标识，允许为 {@code null}
     * @return 可安全作为单级目录名的字符串
     */
    private static String sanitize(String account) {
        if (account == null || account.isBlank()) {
            return SYSTEM_ACCOUNT;
        }
        String s = account.trim()
                .replace("\\", "_").replace("/", "_").replace(":", "_")
                .replace("*", "_").replace("?", "_").replace("\"", "_")
                .replace("<", "_").replace(">", "_").replace("|", "_");
        s = s.replace("..", "_");
        s = s.replaceAll("^[.\\s]+|[.\\s]+$", "");
        return s.isBlank() ? SYSTEM_ACCOUNT : s;
    }

    /**
     * 读取所有向量库的持久化配置。
     *
     * @return SPI 名称到键值配置的映射，解析失败返回空映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readVectorStorageConfigs() {
        String json = settingReader.getStr(GROUP, KEY_VECTOR_STORAGE_CONFIG);
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = Json.fromJson(json);
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception e) {
            log.error("[RagClientManager] 解析 {} 配置失败，按空配置处理: {}",
                    KEY_VECTOR_STORAGE_CONFIG, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 取出宿主数据源，供 MySQL / PostgreSQL 这类「向量直接存业务库」的实现注入。
     *
     * <p>用 {@code ObjectProvider} 延迟取：jvector / milvus 等实现根本不需要数据源，
     * 此时容器里没有 DataSource 也不该导致启动失败。</p>
     *
     * @return 宿主数据源；容器中没有时返回 null
     */
    private DataSource resolveDataSource() {
        try {
            return dataSourceProvider == null ? null : dataSourceProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("[RagClientManager] 获取宿主数据源失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 输出当前 RAG 关键配置的描述信息。
     * @return 键值对形式的 RAG 配置描述
     */
    public Map<String, String> describe() {
        Map<String, String> desc = new LinkedHashMap<>();
        // 上报「模型 + 推导出的引擎」，不再上报已废弃的 rag_embedding_model：
        // 该字段为空时旧展示逻辑会回显成 memory，看着像用户配了个内存引擎。
        String embeddingModelRef = settingReader.getStr(GROUP, "rag_embedding_model_name");
        desc.put("embedding_model", bareModelId(embeddingModelRef));
        String derivedEngine = resolveEmbeddingEngineByModel(embeddingModelRef);
        desc.put("embedding_engine", StringUtils.defaultString(derivedEngine, ""));
        desc.put("llm_model", settingReader.getStr(GROUP, "rag_llm_model"));
        desc.put("vector_storage", settingReader.getStr(GROUP, "rag_vector_storage"));
        desc.put("upload_dir", settingReader.getStr(GROUP, "rag_upload_dir"));
        desc.put("splitter", settingReader.getStr(GROUP, "rag_splitter"));
        desc.put("top_k", String.valueOf(settingReader.getInt(GROUP, "rag_default_top_k", properties.getDefaultTopK())));
        desc.put("similarity_threshold", String.valueOf(settingReader.getDouble(GROUP, "rag_similarity_threshold",
                properties.getDefaultSimilarityThreshold())));
        desc.put("client_provider", StringUtils.hasText(settingReader.getStr(GROUP, "rag_client_provider"))
                ? settingReader.getStr(GROUP, "rag_client_provider") : "pipeline");
        return desc;
    }

    /**
     * 创建文本分割器：按全局配置的切片 SPI 名称解析，未配置或不可用时回退 sentence。
     * @param splitterName 分割器 SPI 扩展名，null 或空时默认 sentence
     * @param chunkSize 分块大小（字符）
     * @param chunkOverlap 相邻分块重叠字符数
     * @return 文本分块器实例，不为 null
     */
    private TextSplitter createTextSplitter(String splitterName, int chunkSize, int chunkOverlap) {
        String name = StringUtils.hasText(splitterName) ? splitterName.trim().toLowerCase() : "sentence";
        try {
            return TextSplitterProvider.create(name, chunkSize, chunkOverlap);
        } catch (Exception e) {
            log.warn("[RagClientManager] 文本分割器 {} 不可用，回退 sentence: {}", name, e.getMessage());
            return new SentenceTextSplitter(chunkSize, chunkOverlap);
        }
    }

    /**
     * 创建 RAG 客户端：优先通过 rag_client_provider 配置的名称查找 SPI，未配置或失败时回退管线实现。
     * @param setting RAG 客户端配置
     * @return RAG 客户端
     */
    private RagClient createClient(RagClientSetting setting) {
        String provider = settingReader.getStr(GROUP, "rag_client_provider");
        if (StringUtils.isBlank(provider) || "pipeline".equalsIgnoreCase(provider.trim())) {
            return new RagPipeline(setting);
        }
        String name = provider.trim().toLowerCase();
        try {
            RagClient client = RagClient.create(name, setting);
            if (client != null) {
                return client;
            }
        } catch (Exception e) {
            log.warn("[RagClientManager] SPI 客户端 {} 创建失败，回退管线实现: {}", name, e.getMessage());
        }
        return new RagPipeline(setting);
    }

    /**
     * 监听 RAG 设置变更事件，重建 RAG 客户端以使用最新的配置。
     * @param event 事件
     */
    @EventListener
    public void onRagSettingsChanged(RagSettingsChangedEvent event) {
        if (event == null || !GROUP.equals(event.group())) {
            return;
        }
        if (event.userScoped()) {
            // 客户端已按账号隔离：定向重建触发者自己那一个即可。
            // 仍重建全部账号的话，会把触发者的 embedding / 切片 / 上传目录
            // 烘进其他账号的客户端，正是这次要消除的串号。
            log.info("[RagClientManager] 收到用户级 rag 配置写入，定向重建该账号的 RAG 客户端");
            reloadUser(event.userId());
            return;
        }
        log.info("[RagClientManager] 收到 rag 设置变更事件，重建 RAG 客户端");
        rebuild();
    }

    /**
     * 应用关闭时关闭全部 RAG 客户端（每个账号一个 + 系统桶一个）。
     *
     * <p>关键：让 {@link RagPipeline#close()} 落盘并关闭向量存储。否则实现层
     * （jvector ON_DISK 等）不会 flush 索引，进程重启后历史文档检索不到。
     * 只关一个是不够的——按账号隔离后每个账号都有自己的索引都要落盘。</p>
     */
    @PreDestroy
    public void destroy() {
        var all = new ArrayList<RagClient>();
        synchronized (clientLock) {
            all.addAll(userClients.values());
            userClients.clear();
            userOrder.clear();
            if (systemClient != null) {
                all.add(systemClient);
                systemClient = null;
            }
        }
        int accountCount = all.size();
        for (RagClient client : all) {
            closeQuietly(client);
        }
        if (!all.isEmpty()) {
            log.info("[RagClientManager] 已在关闭阶段关闭 {} 个 RAG 客户端（索引与分块缓存均已落盘）",
                    accountCount);
        }
    }

    /**
     * 获取当前 RAG 管线实例（仅在 createClient 返回 RagPipeline 时有效）。
     * @return RagPipeline 实例，非管线实现时返回 null
     */
    public RagPipeline currentPipeline() {
        RagClient client = current();
        return client instanceof RagPipeline pipeline ? pipeline : null;
    }
}


