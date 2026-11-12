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
    /**
     * 本次调用归属的路由组（即路由组供应商 ID）。
     *
     * <p>网关请求的模型是「供应商ID:模型名」的复合标识，前缀就是路由组供应商 ID；
     * 控制台直连、服务实例与未带复合标识的调用为空。路由拓扑页按此列过滤，
     * 否则切换路由组时入站流量不会变化，连线会指向错误的因果。</p>
     */
    private String routerGroup;
    private LocalDateTime createTime;
}
