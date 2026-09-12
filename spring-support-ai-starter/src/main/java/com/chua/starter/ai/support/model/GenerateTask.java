package com.chua.starter.ai.support.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
* AI 生成任务（文生图/文生视频）模型。
*
* <p>对应通义万相的异步任务模式：提交请求后返回任务 ID，
* 前端轮询任务状态直至完成，后续可复查历史记录。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
public class GenerateTask {

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

    /**
    * 任务唯一 标识
     */
    private String taskId;

    /**
    * 任务类型：镜像 / 视频
     */
    private String type;

    /**
    * 提示词
     */
    private String prompt;

    /**
    * 使用的模型
     */
    private String model;

    /**
    * 任务状态：处理 / 成功 / 失败
     */
    private String status;

    /**
    * 生成结果 URL（成功后）
     */
    private String resultUrl;

    /**
    * 失败原因
     */
    private String errorMessage;

    /**
    * 创建时间
     */
    private LocalDateTime createTime;

    /**
    * 完成时间
     */
    private LocalDateTime completeTime;
}