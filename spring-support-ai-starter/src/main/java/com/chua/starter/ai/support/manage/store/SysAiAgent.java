package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
   * AI 智能体 定义持久化实体。
 *
 * <p>一行 = 一个 Agent（主 Agent 或子 Agent）。主 Agent 的
 * {@code parentId} 为空；子 Agent 的 {@code parentId} 指向主 Agent。
   * 每个 智能体 可独立配置模型（提供者 + 模型）、MCP（mcp标识）、
   * Skill（skill标识）、系统提示符（instruction / 系统提示符）与策略
 * （strategy JSON）。</p>
 *
 * <p>数据库列以表名 {@code sys_ai_agent_} 作为前缀，实体字段保持简短语义名，
 * 通过 {@link TableField} 显式映射，前端契约稳定不变。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_agent", autoResultMap = true)
public class SysAiAgent implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
       * 智能体 标识（uuidv7）
     */
    @TableId(value = "sys_ai_agent_id", type = IdType.INPUT)
    private String agentId; // 智能体标识

    /**
       * 所属主 智能体 标识（空表示主 智能体）
     */
    @TableField("sys_ai_agent_parent_id")
    private String parentId; // 父标识

    /**
      * 智能体 名称
     */
    @TableField("sys_ai_agent_name")
    private String name; // 名称

    /**
      * 智能体 描述
     */
    @TableField("sys_ai_agent_description")
    private String description; // description

    /**
      * 智能体 角色
     */
    @TableField("sys_ai_agent_role")
    private String role; // 角色

    /**
      * 系统指令（系统 提示符）
     */
    @TableField("sys_ai_agent_instruction")
    private String instruction; // instruction

    /**
     * 完整系统提示词（为空则自动生成）
     */
    @TableField("sys_ai_agent_system_prompt")
    private String systemPrompt; // 系统提示符

    /**
       * 关联提供商 标识（模型 使用）
     */
    @TableField("sys_ai_agent_provider_id")
    private String providerId; // 提供者标识

    /**
     * 关联模型名称
     */
    @TableField("sys_ai_agent_model")
    private String model; // 模型

    /**
     * 关联 MCP 列表（数组）
     */
    @TableField(value = "sys_ai_agent_mcp_ids", typeHandler = JacksonTypeHandler.class)
    private List<String> mcpIds; // mcp标识

    /**
     * 关联 Skill 列表（数组）
     */
    @TableField(value = "sys_ai_agent_skill_ids", typeHandler = JacksonTypeHandler.class)
    private List<String> skillIds; // skill标识

    /**
      * 策略 JSON（mode/planning/内存/最大tooliterations/temperature/最大令牌 等）
     */
    @TableField(value = "sys_ai_agent_strategy", typeHandler = JacksonTypeHandler.class)
    private java.util.Map<String, Object> strategy; // strategy

    /**
     * 是否启用
     */
    @TableField("sys_ai_agent_enabled")
    private Boolean enabled; // 已启用

    /**
     * 排序序号
     */
    @TableField("sys_ai_agent_sort_order")
    private Integer sortOrder; // 排序订单

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_ai_agent_create_time")
    private Long createdAt; // 创建at

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_ai_agent_update_time")
    private Long updatedAt; // 更新at
}
