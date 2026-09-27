package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 服务实例实体，记录服务实例的端口、协议开关和用量记录配置。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_service_instance")
public class SysAiServiceInstance {
    @TableId(type = IdType.INPUT)
    private String instanceId;
    private String instanceName;
    private Integer port;
    private Integer enabled;
    private Integer openaiEnabled;
    private Integer ccEnabled;
    private Integer geminiEnabled;
    private Integer recordUsage;
    private String userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
