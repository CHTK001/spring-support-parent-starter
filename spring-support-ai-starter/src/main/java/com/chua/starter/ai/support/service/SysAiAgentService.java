package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.manage.store.SysAiAgent;

import java.util.List;
import java.util.Map;

/**
 * AI Agent 管理服务。
 *
 * <p>提供主 Agent / 子 Agent 的 CRUD、编排图保存加载、以及将 DB 配置的
 * Agent 动态构建为底层 {@code Agent} 并注册到 {@code AgentRegistry} 的能力。
 * 每个 Agent 可独立配置模型（provider + model）、MCP、Skill、SystemPrompt 与策略。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiAgentService {

    /**
     * 查询全部主 Agent（含子 Agent）。
     *
     * @return 主 Agent 列表
     */
    List<Map<String, Object>> listAgents();

    /**
     * 查询单个 Agent（含子 Agent）。
     *
     * @param agentId Agent ID
     * @return Agent 详情（含子 Agent 列表）
     */
    Map<String, Object> getAgent(String agentId);

    /**
     * 创建主 Agent。
     *
     * @param body 创建请求
     * @return 创建后的 Agent
     */
    Map<String, Object> createAgent(Map<String, Object> body);

    /**
     * 更新主 Agent。
     *
     * @param agentId Agent ID
     * @param body    更新请求
     * @return 更新后的 Agent
     */
    Map<String, Object> updateAgent(String agentId, Map<String, Object> body);

    /**
     * 删除主 Agent（级联删除子 Agent）。
     *
     * @param agentId Agent ID
     */
    void deleteAgent(String agentId);

    /**
     * 创建子 Agent。
     *
     * @param agentId 主 Agent ID
     * @param body    子 Agent 请求
     * @return 创建后的子 Agent
     */
    Map<String, Object> createSubAgent(String agentId, Map<String, Object> body);

    /**
     * 更新子 Agent。
     *
     * @param agentId 主 Agent ID
     * @param subId   子 Agent ID
     * @param body    更新请求
     * @return 更新后的子 Agent
     */
    Map<String, Object> updateSubAgent(String agentId, String subId, Map<String, Object> body);

    /**
     * 删除子 Agent。
     *
     * @param agentId 主 Agent ID
     * @param subId   子 Agent ID
     */
    void deleteSubAgent(String agentId, String subId);

    /**
     * 保存编排图（节点 + 连线），持久化到编排配置。
     *
     * @param agentId Agent ID
     * @param body    { nodes, edges }
     * @return 保存结果
     */
    Map<String, Object> saveOrchestration(String agentId, Map<String, Object> body);

    /**
     * 加载编排图。
     *
     * @param agentId Agent ID
     * @return { nodes, edges, mode }
     */
    Map<String, Object> getOrchestration(String agentId);

    /**
     * 将 Agent 注册为模型（Agent-as-Model），使其可被对话链路选择。
     *
     * @param agentId   Agent ID
     * @param modelName 模型名（可选，缺省 {@code agent-<name>}）
     * @return 注册结果
     */
    Map<String, Object> registerAsModel(String agentId, String modelName);

    /**
     * 构建并注册 Agent 到 AgentRegistry（重建运行时）。
     *
     * @param agent DB Agent 配置
     */
    void rebuildAgent(SysAiAgent agent);

    /**
     * 按主 Agent 名从运行时重建（供编排保存后热更新）。
     *
     * @param agentId Agent ID
     */
    void rebuildAgentById(String agentId);
}
