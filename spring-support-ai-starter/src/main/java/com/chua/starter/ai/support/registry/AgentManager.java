package com.chua.starter.ai.support.registry;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentLoopGuard;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.starter.ai.support.configuration.runtime.AgentProviderFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时 Agent 管理器：按 id 持有并运维已经创建出来的 Agent 实例。
 *
 * <p>与 {@link AgentRegistry} 的分工：{@code AgentRegistry} 是「按名称的查找索引」，
 * 供对话路由按能力/分组挑 Agent；本类管的是「我创建过哪些 Agent、它们现在什么状态、
 * 要停哪一个」——也就是运维面。两者都持有同一批实例引用，不复制、不重建。</p>
 *
 * <h2>为什么要有它</h2>
 * <p>Agent 一旦跑起来，外部要能：按 id 拿到实例（{@link #get(String)}）、看它是否在跑、
 * 是否被判定为原地打转（{@link #status(String)}）、以及把它停下来（{@link #interrupt(String)} /
 * {@link #destroy(String)}）。没有这层，{@code interrupt()} 与 {@code loopGuard()} 只在
 * Agent 内部可见，接口与管理页都无从下手。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 *   Agent agent = agentManager.get("147");      // 按 id 取运行时实例
 *   agentManager.interrupt("147");              // 停掉它当前这一轮（关掉它起的进程/Harness）
 *   Map<String, Object> status = agentManager.status("147");
 * }</pre>
 *
 * @since 4.0.0.48
 * @author CH
 */
@Component
public class AgentManager {

    /**
     * Agent 引擎工厂：按管理员配置的引擎实现创建实例。
     */
    private final AgentProviderFactory agentProviderFactory;

    /**
     * 按 id 索引的运行时 Agent（id 为业务侧标识，通常是 sys_ai_agent.agent_id）。
     */
    private final Map<String, ManagedAgent> agents = new ConcurrentHashMap<>();

    public AgentManager(AgentProviderFactory agentProviderFactory) {
        this.agentProviderFactory = agentProviderFactory;
    }

    /**
     * 按 id 创建一个 Agent 并纳入托管。
     *
     * <p>同 id 已存在时直接返回已托管的实例，不重复创建：重复创建会让「按 id 停止」
     * 只能停到其中一个，是运维事故的常见来源。</p>
     *
     * @param id         Agent 标识（业务侧 id），不能为空
     * @param definition Agent 定义，可空（空则按 id 建一个同名定义）
     * @param chatClient 对话客户端，可空
     * @return 托管中的 Agent 实例
     */
    public Agent create(String id, AgentDefinition definition, ChatClient chatClient) {
        String agentId = requireId(id);
        Agent existing = get(agentId);
        if (existing != null) {
            return existing;
        }
        Agent agent = agentProviderFactory.createAgent();
        if (agent == null) {
            throw new IllegalStateException("Agent 运行时不可用（缺少 SPI 实现）: "
                    + agentProviderFactory.resolveProvider());
        }
        AgentDefinition effective = definition != null ? definition : AgentDefinition.builder()
                .id(agentId)
                .name(agentId)
                .description("")
                .build();
        agent.definition(effective);
        if (chatClient != null) {
            agent.chatClient(chatClient);
        }
        return register(agentId, agent);
    }

    /**
     * 把已经构建好的 Agent 纳入托管（不重建实例）。
     *
     * <p>业务侧（如 {@code SysAiAgentServiceImpl}）自己按库里的配置装配 Agent，
     * 装配完交给这里登记，运维面才按 id 找得到它。</p>
     *
     * @param id    Agent 标识
     * @param agent 已构建的实例
     * @return 同一个实例，便于链式调用
     */
    public Agent register(String id, Agent agent) {
        String agentId = requireId(id);
        if (agent == null) {
            throw new IllegalArgumentException("Agent 实例不能为空: id=" + agentId);
        }
        ManagedAgent existed = agents.put(agentId, new ManagedAgent(agentId, agent));
        if (existed != null && existed.agent != agent) {
            // 同 id 换实现：旧实例已经被新实例取代，别让它继续留在后台跑。
            closeQuietly(existed.agent);
        }
        return agent;
    }

    /**
     * 按 id 取运行时 Agent。
     *
     * @param id Agent 标识
     * @return Agent 实例；未托管时返回 {@code null}
     */
    public Agent get(String id) {
        String agentId = normalise(id);
        if (agentId == null) {
            return null;
        }
        ManagedAgent managed = agents.get(agentId);
        if (managed == null) {
            return null;
        }
        managed.touch();
        return managed.agent;
    }

    /**
     * 按 id 取运行时 Agent，取不到就抛异常。
     *
     * @param id Agent 标识
     * @return Agent 实例
     * @throws IllegalStateException 未托管
     */
    public Agent require(String id) {
        Agent agent = get(id);
        if (agent == null) {
            throw new IllegalStateException("Agent 未托管: id=" + normalise(id));
        }
        return agent;
    }

    /**
     * 是否已托管该 id。
     *
     * @param id Agent 标识
     * @return true 表示已托管
     */
    public boolean contains(String id) {
        String agentId = normalise(id);
        return agentId != null && agents.containsKey(agentId);
    }

    /**
     * 列出全部托管的 Agent（按创建顺序）。
     *
     * @return 托管记录列表（只读）
     */
    public List<ManagedAgent> list() {
        return Collections.unmodifiableList(new ArrayList<>(agents.values()));
    }

    /**
     * 全部托管 Agent 的状态快照（供管理页列表）。
     *
     * @return 状态列表
     */
    public List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ManagedAgent managed : list()) {
            result.add(snapshotOf(managed));
        }
        return result;
    }

    /**
     * 单个 Agent 的状态：定义、运行时实现、是否已中断、连续重复次数与最近一次工具。
     *
     * @param id Agent 标识
     * @return 状态；未托管时返回 {@code null}
     */
    public Map<String, Object> status(String id) {
        ManagedAgent managed = managed(id);
        return managed == null ? null : snapshotOf(managed);
    }

    /**
     * 停掉该 Agent 当前这一轮：三个实现各自落地（opencode 强杀 CLI 子进程，
     * agentscope 关闭当前 Harness，langchain4j 在循环点退出）。
     *
     * <p>只停当前一轮，实例仍可继续用；要连实例一起销毁用 {@link #destroy(String)}。</p>
     *
     * @param id Agent 标识
     * @return true 表示找到并发出中断；false 表示未托管
     */
    public boolean interrupt(String id) {
        ManagedAgent managed = managed(id);
        if (managed == null) {
            return false;
        }
        managed.touch();
        try {
            managed.agent.interrupt();
            managed.interruptCount.incrementAndGet();
            return true;
        } catch (Exception e) {
            managed.lastError = String.valueOf(e.getMessage());
            return false;
        }
    }

    /**
     * 停掉全部托管 Agent 当前这一轮。
     *
     * @return 实际发出中断的个数
     */
    public int interruptAll() {
        int count = 0;
        for (ManagedAgent managed : list()) {
            if (interrupt(managed.id)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 销毁：先中断当前一轮，再关闭实例并移出托管。
     *
     * @param id Agent 标识
     * @return true 表示确实销毁了一个托管实例
     */
    public boolean destroy(String id) {
        String agentId = normalise(id);
        if (agentId == null) {
            return false;
        }
        ManagedAgent managed = agents.remove(agentId);
        if (managed == null) {
            return false;
        }
        interruptQuietly(managed.agent);
        closeQuietly(managed.agent);
        return true;
    }

    /**
     * 已托管数量。
     *
     * @return 数量
     */
    public int size() {
        return agents.size();
    }

    /**
     * 清空托管并关闭全部实例（容器销毁或热重建时用）。
     */
    public void closeAll() {
        for (String id : new ArrayList<>(agents.keySet())) {
            destroy(id);
        }
    }

    /**
     * 组装单个 Agent 的状态。
     *
     * @param managed 托管记录
     * @return 状态字段
     */
    private Map<String, Object> snapshotOf(ManagedAgent managed) {
        AgentLoopGuard guard = guardOf(managed.agent);
        AgentDefinition definition = definitionOf(managed.agent);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", managed.id);
        state.put("name", definition != null && definition.getName() != null
                ? definition.getName() : managed.id);
        state.put("description", definition != null && definition.getDescription() != null
                ? definition.getDescription() : "");
        state.put("leader", definition != null && definition.isLeader());
        state.put("definitionId", definition != null ? definition.getId() : null);
        state.put("subAgentCount", definition != null && definition.getAgents() != null
                ? definition.getAgents().size() : 0);
        state.put("implementation", managed.agent.getClass().getName());
        state.put("interrupted", guard != null && guard.isInterrupted());
        state.put("consecutiveRepeats", guard != null ? guard.consecutiveRepeats() : 0);
        state.put("lastTool", guard != null ? guard.lastToolName() : null);
        state.put("repeatThreshold", guard != null ? guard.repeatThreshold() : 0);
        state.put("interruptCount", managed.interruptCount.get());
        state.put("createdAt", managed.createdAt);
        state.put("lastAccessAt", managed.lastAccessAt);
        state.put("lastError", managed.lastError);
        return state;
    }

    /**
     * 取托管记录（内部用，不做校验）。
     *
     * @param id Agent 标识
     * @return 托管记录；未托管时返回 {@code null}
     */
    private ManagedAgent managed(String id) {
        String agentId = normalise(id);
        return agentId == null ? null : agents.get(agentId);
    }

    /**
     * 取 Agent 的护栏：实现方没设过护栏时返回 {@code null}（旧实现没有这条链路）。
     *
     * @param agent Agent 实例
     * @return 护栏或 {@code null}
     */
    private static AgentLoopGuard guardOf(Agent agent) {
        try {
            return agent.loopGuard();
        } catch (RuntimeException e) {
            // 个别实现可能不支持护栏：状态里显示为「无」，不让查询整体失败。
            return null;
        }
    }

    /**
     * 取 Agent 的定义：实现方没暴露时返回 {@code null}。
     *
     * @param agent Agent 实例
     * @return 定义或 {@code null}
     */
    private static AgentDefinition definitionOf(Agent agent) {
        try {
            return agent.getDefinition();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 静默中断。
     *
     * @param agent Agent 实例
     */
    private static void interruptQuietly(Agent agent) {
        try {
            agent.interrupt();
        } catch (RuntimeException ig) {
            // 销毁流程里中断失败不该挡住关闭：下面还会 close()。
        }
    }

    /**
     * 静默关闭。
     *
     * @param agent Agent 实例
     */
    private static void closeQuietly(Agent agent) {
        try {
            agent.close();
        } catch (RuntimeException ig) {
            // 关闭失败只影响资源回收，不向上抛：调用方正在做的是替换或销毁。
        }
    }

    /**
     * 校验必填 id。
     *
     * @param id 原始 id
     * @return 规范化后的 id
     */
    private static String requireId(String id) {
        String agentId = normalise(id);
        if (agentId == null) {
            throw new IllegalArgumentException("Agent id 不能为空");
        }
        return agentId;
    }

    /**
     * 规范化 id：去空白，空串视为空。
     *
     * @param id 原始 id
     * @return 规范化后的 id 或 {@code null}
     */
    private static String normalise(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 一条托管记录：实例 + 运维侧关心的少量状态。
     */
    public static final class ManagedAgent {

        /**
         * Agent 标识
         */
        private final String id;

        /**
         * 运行时实例
         */
        private final Agent agent;

        /**
         * 纳入托管的时间
         */
        private final long createdAt = System.currentTimeMillis();

        /**
         * 最近一次被访问/中断的时间
         */
        private volatile long lastAccessAt = System.currentTimeMillis();

        /**
         * 累计发出中断次数（按 id 每次中断计一次）
         */
        private final java.util.concurrent.atomic.AtomicInteger interruptCount =
                new java.util.concurrent.atomic.AtomicInteger();

        /**
         * 最近一次中断失败的原因（成功时保持原值）
         */
        private volatile String lastError;

        ManagedAgent(String id, Agent agent) {
            this.id = id;
            this.agent = agent;
        }

        /**
         * 记录一次访问。
         */
        void touch() {
            this.lastAccessAt = System.currentTimeMillis();
        }

        /**
         * Agent 标识。
         *
         * @return id
         */
        public String getId() {
            return id;
        }

        /**
         * 运行时实例。
         *
         * @return Agent
         */
        public Agent getAgent() {
            return agent;
        }

        /**
         * 纳入托管的时间。
         *
         * @return 毫秒时间戳
         */
        public long getCreatedAt() {
            return createdAt;
        }

        /**
         * 最近一次访问的时间。
         *
         * @return 毫秒时间戳
         */
        public long getLastAccessAt() {
            return lastAccessAt;
        }
    }
}
