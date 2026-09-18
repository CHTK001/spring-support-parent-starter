package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.manage.store.SysAiAgent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* AI 智能体 管理服务。
*
* <p>提供主 Agent / 子 Agent 的 CRUD、编排图保存加载、以及将 DB 配置的
* 智能体 动态构建为底层 {@code Agent} 并注册到 {@code AgentRegistry} 的能力。
* 每个 智能体 可独立配置模型（提供者 + 模型）、MCP、Skill、系统提示符 与策略。</p>
*
* @since 4.0.0.42
* @author CH
 */
public interface SysAiAgentService {

    /**
    * 查询全部主 智能体（含子 智能体）。
    *
    * @return 主 智能体 列表
    */
    List<Map<String, Object>> listAgents();

    /**
    * 查询单个 智能体（含子 智能体）。
    *
    * @param agentId 智能体 标识
    * @return Agent 详情（含子 智能体 列表）
    */
    Map<String, Object> getAgent(String agentId);

    /**
    * 创建主 智能体。
    *
    * @param body 创建请求
    * @return 创建后的 智能体
    */
    Map<String, Object> createAgent(Map<String, Object> body);

    /**
    * 更新主 智能体。
    *
    * @param agentId 智能体 标识
    * @param body    更新请求
    * @return 更新后的 智能体
    */
    Map<String, Object> updateAgent(String agentId, Map<String, Object> body);

    /**
    * 删除主 智能体（级联删除子 智能体）。
    *
    * @param agentId 智能体 标识
    */
    void deleteAgent(String agentId);

    /**
    * 创建子 智能体。
    *
    * @param agentId 主 智能体 标识
    * @param body    子 智能体 请求
    * @return 创建后的子 智能体
    */
    Map<String, Object> createSubAgent(String agentId, Map<String, Object> body);

    /**
    * 更新子 智能体。
    *
    * @param agentId 主 智能体 标识
    * @param subId   子 智能体 标识
    * @param body    更新请求
    * @return 更新后的子 智能体
    */
    Map<String, Object> updateSubAgent(String agentId, String subId, Map<String, Object> body);

    /**
    * 删除子 智能体。
    *
    * @param agentId 主 智能体 标识
    * @param subId   子 智能体 标识
    */
    void deleteSubAgent(String agentId, String subId);

    /**
    * 保存编排图（节点 + 连线），持久化到编排配置。
    *
    * @param agentId 智能体 标识
    * @param body    { 节点, edges }
    * @return 保存结果
    */
    Map<String, Object> saveOrchestration(String agentId, Map<String, Object> body);

    /**
    * 加载编排图。
    *
    * @param agentId 智能体 标识
    * @return { 节点, edges, mode }
    */
    Map<String, Object> getOrchestration(String agentId);

    /**
    * 按编排图拓扑顺序串行执行子 Agent（上一个输出作为下一个输入），汇总返回。
    *
    * @param agentId 主 智能体 标识
    * @param input   初始输入
    * @return 各子 Agent 输出与最终汇总输出
    */
    Map<String, Object> runOrchestration(String agentId, String input);

    /**
    * 将 智能体 注册为模型（智能体-as-模型），使其可被对话链路选择。
    *
    * @param agentId   智能体 标识
    * @param modelName 模型名（可选，缺省 {@code agent-<name>}）
    * @return 注册结果
    */
    Map<String, Object> registerAsModel(String agentId, String modelName);

    /**
    * 取消注册 智能体 模型，从对话注册中心移除。
    *
    * @param agentId 智能体 标识
    */
    void unregisterModel(String agentId);

    /**
    * 查询 智能体 是否已注册为模型。
    *
    * @param agentId 智能体 标识
    * @return 注册状态（含 模型名称，未注册时 模型_名称 为 空）
    */
    Map<String, Object> getRegisteredModelInfo(String agentId);

    /**
    * 获取所有已注册的 智能体 模型名称。
    *
    * @return 模型名称集合
    */
    Set<String> getRegisteredAgentModelNames();

    /**
    * 构建并注册 智能体 到 智能体registry（重建运行时）。
    *
    * @param agent DB 智能体 配置
    */
    void rebuildAgent(SysAiAgent agent);

    /**
    * 按主 智能体 名从运行时重建（供编排保存后热更新）。
    *
    * @param agentId 智能体 标识
    */
    void rebuildAgentById(String agentId);
}
