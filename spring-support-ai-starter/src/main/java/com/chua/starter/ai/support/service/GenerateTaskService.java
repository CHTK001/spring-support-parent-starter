package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.GenerateTask;

/**
 * AI 生成任务服务接口（文生图 / 文生视频）。
 *
 * <p>提供异步生成任务的创建与状态查询，供前端轮询使用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface GenerateTaskService {

    /**
     * 创建文生图任务。
     *
     * @param prompt   提示词
     * @param model    模型
     * @param size     尺寸（可选）
     * @param style    风格（可选）
     * @param quality  质量（可选）
     * @return 生成任务
     */
    GenerateTask createImageTask(String prompt, String model, String size, String style, String quality);

    /**
     * 创建文生视频任务。
     *
     * @param prompt   提示词
     * @param model    模型
     * @param duration 时长（秒）
     * @param size     尺寸（可选）
     * @param style    风格（可选）
     * @return 生成任务
     */
    GenerateTask createVideoTask(String prompt, String model, Integer duration, String size, String style);

    /**
     * 查询任务状态。
     *
     * @param taskId 任务 ID
     * @return 生成任务，不存在时返回 {@code null}
     */
    GenerateTask getTask(String taskId);

    /**
     * 查询最近的任务列表（历史记录）。
     *
     * @param type  任务类型（image/video），可为 {@code null}
     * @param limit 数量限制
     * @return 任务列表
     */
    java.util.List<GenerateTask> listRecent(String type, int limit);
}