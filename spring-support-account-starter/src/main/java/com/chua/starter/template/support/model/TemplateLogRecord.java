package com.chua.starter.template.support.model;

import com.chua.starter.template.support.entity.TemplateLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板调用日志（对外模型）
 *
 * <p>与前端 {@code LogRecord} 一一对应。{@code parameters} 在库里是 JSON 串，
 * 出参/入参时由调用方负责序列化转换。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Data
public class TemplateLogRecord {

    /** 日志 ID */
    private String id;

    /** 模板 ID（同步类操作记录提供商 ID） */
    private String templateId;

    /** 模板名称（同步类操作记录提供商名称） */
    private String templateName;

    /** 操作：preview / design / print / sync */
    private String action;

    /** 调用参数（JSON 串） */
    private String parameters;

    /** 创建时间 */
    private LocalDateTime time;

    /** 调用方 IP */
    private String ip;

    /** 操作人 */
    private String operator;

    /**
     * 实体转对外模型
     *
     * @param entity 日志实体
     * @return 对外模型
     */
    public static TemplateLogRecord of(TemplateLog entity) {
        TemplateLogRecord record = new TemplateLogRecord();
        if (entity == null) {
            return record;
        }
        record.setId(entity.getTemplateLogId());
        record.setTemplateId(entity.getTemplateLogTemplateId());
        record.setTemplateName(entity.getTemplateLogTemplateName());
        record.setAction(entity.getTemplateLogAction());
        record.setParameters(entity.getTemplateLogParameters());
        record.setTime(entity.getTemplateLogCreatedAt());
        record.setIp(entity.getTemplateLogIp());
        record.setOperator(entity.getTemplateLogOperator());
        return record;
    }

    /**
     * 对外模型转实体
     *
     * @return 日志实体
     */
    public TemplateLog toEntity() {
        TemplateLog entity = new TemplateLog();
        entity.setTemplateLogId(id);
        entity.setTemplateLogTemplateId(templateId);
        entity.setTemplateLogTemplateName(templateName);
        entity.setTemplateLogAction(action);
        entity.setTemplateLogParameters(parameters);
        entity.setTemplateLogCreatedAt(time == null ? LocalDateTime.now() : time);
        entity.setTemplateLogIp(ip);
        entity.setTemplateLogOperator(operator);
        return entity;
    }
}
