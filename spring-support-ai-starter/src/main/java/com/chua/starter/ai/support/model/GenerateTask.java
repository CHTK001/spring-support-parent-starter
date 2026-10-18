package com.chua.starter.ai.support.model;

import java.time.LocalDateTime;

/**
 * AI 生成任务（文生图/文生视频）模型。
 *
 * <p>对应通义万相的异步任务模式：提交请求后返回任务 ID，
 * 前端轮询任务状态直至完成，后续可复查历史记录。</p>
 *
 * @param taskId       任务唯一标识
 * @param type         任务类型：镜像 / 视频
 * @param prompt       提示词
 * @param model        使用的模型
 * @param status       任务状态：处理 / 成功 / 失败
 * @param resultUrl    生成结果 URL（成功后）
 * @param errorMessage 失败原因
 * @param createTime   创建时间
 * @param completeTime 完成时间
 * @since 4.0.0.42
 * @author CH
 */
public record GenerateTask(
        String taskId,
        String type,
        String prompt,
        String model,
        String status,
        String resultUrl,
        String errorMessage,
        LocalDateTime createTime,
        LocalDateTime completeTime) {

    /**
     * 任务状态：处理中
     */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /**
     * 任务状态：成功
     */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 任务状态：失败
     */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 任务类型：文生图
     */
    public static final String TYPE_IMAGE = "image";

    /**
     * 任务类型：文生视频
     */
    public static final String TYPE_VIDEO = "video";
}
