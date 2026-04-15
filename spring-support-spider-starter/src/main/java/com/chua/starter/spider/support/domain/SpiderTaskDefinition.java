package com.chua.starter.spider.support.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 爬虫任务定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("spider_task")
public class SpiderTaskDefinition {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 任务唯一编码（全局唯一） */
    @TableField("task_code")
    private String taskCode;

    /** 任务名称 */
    @TableField("task_name")
    private String taskName;

    /** 入口 URL / Seed */
    @TableField("entry_url")
    private String entryUrl;

    /** 任务说明 */
    private String description;

    /** 任务标签/场景分类（逗号分隔） */
    private String tags;

    /** 认证方式（如 NONE、BASIC、COOKIE、TOKEN） */
    @TableField("auth_type")
    private String authType;

    /** 执行类型 */
    @TableField("execution_type")
    private SpiderExecutionType executionType;

    /** 执行策略（序列化为 JSON 存储） */
    @TableField("execution_policy")
    private String executionPolicy;

    /** AI 配置（序列化为 JSON 存储） */
    @TableField("ai_profile")
    private String aiProfile;

    /** 凭证引用（序列化为 JSON 存储，不含明文密码） */
    @TableField("credential_ref")
    private String credentialRef;

    /** 任务状态 */
    private SpiderTaskStatus status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
