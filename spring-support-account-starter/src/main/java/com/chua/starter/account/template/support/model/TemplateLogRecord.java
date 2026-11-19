package com.chua.starter.account.template.support.model;

import com.chua.starter.account.template.support.entity.TemplateLog;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板调用日志（对外模型）
 *
 * <p>与前端 {@code LogRecord} 一一对应。{@code parameters} 在库里是 JSON 串，
 * 由本模型的 {@code parse()}/{@code stringify()} 负责与 JSON 对象互转，并兼容历史脏数据原样返回。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Data
public class TemplateLogRecord {

    /** JSON 序列化/反序列化（parameters 在库里是 JSON 串，出参给对象） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 日志 ID */
    private String id;

    /** 模板 ID（同步类操作记录提供商 ID） */
    private String templateId;

    /** 模板名称（同步类操作记录提供商名称） */
    private String templateName;

    /** 操作：preview / design / print / sync */
    private String action;

    /** 调用参数（JSON 对象，落库时序列化成串） */
    private Object parameters;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
        record.setParameters(parse(entity.getTemplateLogParameters()));
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
        entity.setTemplateLogParameters(stringify(parameters));
        entity.setTemplateLogCreatedAt(time == null ? LocalDateTime.now() : time);
        entity.setTemplateLogIp(ip);
        entity.setTemplateLogOperator(operator);
        return entity;
    }

    /**
     * 库内 JSON 串转对象（脏数据原样返回，不因一条日志解析失败影响列表）
     *
     * @param json JSON 串
     * @return 解析后的对象；空串返回 {@code null}
     */
    private static Object parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return JSON.readValue(json, Object.class);
        } catch (Exception ignored) {
            return json;
        }
    }

    /**
     * 对象转库内 JSON 串
     *
     * @param value 参数对象
     * @return JSON 串；空值返回 {@code null}
     */
    private static String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }
}
