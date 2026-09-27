package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.AiMemoryDTO;
import com.chua.starter.ai.support.model.AiMemoryMaintenanceDTO;
import com.chua.starter.ai.support.model.AiMemoryRecallDTO;
import com.chua.starter.ai.support.model.AiMemoryUpsertRequest;
import com.chua.starter.ai.support.model.MemoryTuningDTO;

import java.util.List;

/**
 * AI 记忆体服务 —— 记忆能力对外的统一编排入口。
 *
 * <p>四段职责与 {@code SysAiMemoryStore} 的分工：本服务负责读取设置分组 {@code memory} 的运行参数、
 * 组装注入片段、调度抽取与维护；存储层负责归属校验、打分与落库。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiMemoryService {

    /**
     * 记录一条记忆（手动或抽取来源都走这里）。
     *
     * @param userId         归属用户标识
     * @param request        写入内容
     * @param source         来源标记，如 manual/auto-extract
     * @return 落库后的记忆
     */
    AiMemoryDTO record(String userId, AiMemoryUpsertRequest request, String source);

    /**
     * 管理列表。
     *
     * @param scope          作用域筛选，可空
     * @param conversationId 会话 标识筛选，可空
     * @param keyword        关键词筛选，可空
     * @param status         状态筛选，可空
     * @param limit          条数上限
     * @return 记忆列表
     */
    List<AiMemoryDTO> list(String scope, String conversationId, String keyword, String status, int limit);

    /**
     * 读取单条记忆（读门禁）。
     *
     * @param memoryId 记忆 标识
     * @return 记忆
     */
    AiMemoryDTO get(String memoryId);

    /**
     * 更新单条记忆（写门禁）。
     *
     * @param memoryId 记忆 标识
     * @param request  更新内容（非空字段生效）
     * @return 更新后的记忆
     */
    AiMemoryDTO update(String memoryId, AiMemoryUpsertRequest request);

    /**
     * 删除单条记忆（写门禁）。
     *
     * @param memoryId 记忆 标识
     */
    void delete(String memoryId);

    /**
     * 召回并组装注入片段（自动使用）。
     *
     * @param userId         归属用户
     * @param conversationId 当前会话 标识
     * @param query          本轮用户输入
     * @param inject         是否回写召回统计（试算时传 false）
     * @return 召回结果（含注入片段与命中条目）
     */
    AiMemoryRecallDTO recall(String userId, String conversationId, String query, boolean inject);

    /**
     * 一轮对话结束后抽取并落库记忆（自动记录）。
     *
     * <p>身份由调用方在请求线程上捕获后传入，回调线程取不到登录态。</p>
     *
     * @param userId         归属用户
     * @param conversationId 会话 标识
     * @param userMessage    本轮用户输入
     * @param assistantReply 本轮模型回复
     * @param model          本轮模型名称
     * @return 实际新增或合并的条数
     */
    int extractAndRecord(String userId, String conversationId, String userMessage, String assistantReply, String model);

    /**
     * 维护单个用户的记忆库。
     *
     * @param userId 归属用户
     * @return 各动作条数
     */
    AiMemoryMaintenanceDTO maintain(String userId);

    /**
     * 维护全部有记忆的用户（后台定时任务入口）。
     *
     * @return 各动作条数合计
     */
    AiMemoryMaintenanceDTO maintainAll();

    /**
     * 读取记忆体运行参数（设置分组 {@code memory} 的当前生效值）。
     *
     * @return 运行参数
     */
    MemoryTuningDTO tuning();
}
