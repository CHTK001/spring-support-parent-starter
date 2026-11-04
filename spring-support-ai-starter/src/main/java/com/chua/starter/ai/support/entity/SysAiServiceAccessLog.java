package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 服务访问日志实体，记录服务实例收到的请求、令牌用量、延迟和响应状态。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_service_access_log")
public class SysAiServiceAccessLog {
    @TableId(type = IdType.AUTO)
    private Long logId;
    private String instanceId;
    private String endpoint;
    private String method;
    private String model;
    private String protocol;
    private Integer status;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long latencyMs;
    private String clientIp;
    private String userId;
    /** 本次调用使用的 AppKey（服务实例与网关统一按 AppKey 鉴权） */
    private String appKey;
    private LocalDateTime createTime;
}
