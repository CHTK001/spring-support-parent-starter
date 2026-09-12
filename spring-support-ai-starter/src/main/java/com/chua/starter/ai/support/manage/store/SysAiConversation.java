package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
* 前端会话实体（对齐前端 {@code TChatConversation}），同时作为 MyBatis-Plus ORM 实体。
*
* <p>会话主键为 {@code conversationId}（UUIDv7 字符串），由后端生成，前端不传。
* 持久化到 {@code sys_ai_conversation} 表。</p>
*
* <p>数据库列以表名 {@code sys_ai_conversation_} 作为前缀，实体字段保持简短语义名，
* 通过 {@link TableField} 显式映射，前端契约稳定不变。</p>
*
* <p>JSON 字段说明：
* <ul>
*   <li>{@code model} / {@code extra} 为结构化 Map，通过 {@link JacksonTypeHandler}
* 序列化为 文本 存储（列名按表前缀映射）；</li>
*   <li>{@code runtime} 为运行时状态，不入库（{@link TableField#exist()}=false）。</li>
* </ul>
* </p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName(value = "sys_ai_conversation", autoResultMap = true)
public class SysAiConversation implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
    * 会话主键（uuidv7 字符串），由后端 {@code newId()} 生成并填充
     */
    @TableId(value = "sys_ai_conversation_id", type = IdType.INPUT)
    private String conversationId; // conversationid

    /**
    * 会话类型（当前仅 nomi）
     */
    @TableField("sys_ai_conversation_type")
    private String type = "nomi"; // 类型

    /**
    * 会话名称
     */
    @TableField("sys_ai_conversation_name")
    private String name; // 名称

    /**
    * 绑定的模型信息：{提供者_标识, 模型, use_模型?}，JSON 序列化存储
     */
    @TableField(value = "sys_ai_conversation_model", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> model; // 模型

    /**
    * 扩展字段（合并语义，前端 extra 字段透传存储），JSON 序列化存储
     */
    @TableField(value = "sys_ai_conversation_extra", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra; // extra

    /**
    * 是否置顶
     */
    @TableField("sys_ai_conversation_pinned")
    private boolean pinned = false; // pinned

    /**
    * 置顶时间（轮次 毫秒，未置顶为 空）
     */
    @TableField("sys_ai_conversation_pinned_at")
    private Long pinnedAt; // pinnedat

    /**
    * 创建时间（轮次 毫秒）
     */
    @TableField("sys_ai_conversation_created_at")
    private Long createdAt; // 创建at

    /**
    * 修改时间（轮次 毫秒）
     */
    @TableField("sys_ai_conversation_modified_at")
    private Long modifiedAt; // modifiedat

    /**
    * 关联的执行模板 标识
     */
    @TableField("sys_ai_conversation_execution_template_id")
    private String executionTemplateId; // 执行templateid

    /**
    * 运行时状态（如 活跃_turn_标识），仅内存使用，不持久化
     */
    @TableField(exist = false)
    private Map<String, Object> runtime; // runtime
}