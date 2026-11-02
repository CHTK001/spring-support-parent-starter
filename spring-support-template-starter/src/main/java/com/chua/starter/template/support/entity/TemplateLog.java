package com.chua.starter.template.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板调用日志实体
 *
 * <p>对应数据库表 {@code template_log}。记录模板被预览 / 设计 / 打印 / 同步的轨迹，
 * 只作审计用，不影响任何业务数据。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Data
@TableName("template_log")
public class TemplateLog {

    /**
     * 主键 标识
     */
    @TableId(value = "template_log_id", type = IdType.ASSIGN_UUID)
    private String templateLogId;

    /**
     * 模板 ID（同步类操作记录提供商 ID）
     */
    @TableField("template_log_template_id")
    private String templateLogTemplateId;

    /**
     * 模板名称（同步类操作记录提供商名称）
     */
    @TableField("template_log_template_name")
    private String templateLogTemplateName;

    /**
     * 操作：preview / design / print / sync
     */
    @TableField("template_log_action")
    private String templateLogAction;

    /**
     * 调用参数（JSON 串，打印时为打印机与份数）
     */
    @TableField("template_log_parameters")
    private String templateLogParameters;

    /**
     * 调用方 IP
     */
    @TableField("template_log_ip")
    private String templateLogIp;

    /**
     * 操作人
     */
    @TableField("template_log_operator")
    private String templateLogOperator;

    /**
     * 创建时间
     */
    @TableField("template_log_created_at")
    private LocalDateTime templateLogCreatedAt;
}
