package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
* MCP 服务器档案持久化实体。
*
* <p>对应前端 {@code /api/mcp/*} 契约（{@code IMcpServer}），
* 持久化到 {@code sys_ai_mcp_server} 表。</p>
*
* <p>transport 为 JSON 文本存储（type/url/command/args/env/headers），
* 由后端序列化/反序列化，前端契约稳定不变。</p>
*
* <p>数据库列以表名 {@code sys_ai_mcp_server_} 作为前缀，实体字段保持简短语义名，
* 通过 {@link TableField} 显式映射。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("sys_ai_mcp_server")
public class SysAiMcpServer implements Serializable {

    /**
    * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
    * MCP 服务器主键（uuidv7 字符串），由后端 {@code newId()} 生成并填充
    */
    @TableId(value = "sys_ai_mcp_server_id", type = IdType.INPUT)
    private String mcpServerId; // mcp服务端标识

    /**
    * 服务器显示名称
    */
    @TableField("sys_ai_mcp_server_name")
    private String name; // 名称

    /**
    * 描述
    */
    @TableField("sys_ai_mcp_server_description")
    private String description; // description

    /**
    * 传输配置 JSON（类型/url/命令/参数/env/头部）
    */
    @TableField("sys_ai_mcp_server_transport")
    private String transportJson; // transportjson

    /**
    * 原始 JSON 配置（用于编辑时准确回显）
    */
    @TableField("sys_ai_mcp_server_original_json")
    private String originalJson; // 原始json

    /**
    * 是否内置（1=内置，隐藏编辑/删除）
    */
    @TableField("sys_ai_mcp_server_builtin")
    private Boolean builtin; // builtin

    /**
    * 是否默认启用（新会话默认勾选）
    */
    @TableField("sys_ai_mcp_server_enabled")
    private Boolean enabled; // 已启用

    /**
    * 最近测试状态：连接/断开连接/错误/测试
    */
    @TableField("sys_ai_mcp_server_last_test_status")
    private String lastTestStatus; // 最后一个测试状态

    /**
    * 最近连接成功时间（毫秒）
    */
    @TableField("sys_ai_mcp_server_last_connected_at")
    private Long lastConnectedAt; // 最后一个连接at

    /**
    * 创建时间（毫秒）
    */
    @TableField("sys_ai_mcp_server_created_at")
    private Long createdAt; // 创建at

    /**
    * 更新时间（毫秒）
    */
    @TableField("sys_ai_mcp_server_updated_at")
    private Long updatedAt; // 更新at
}
