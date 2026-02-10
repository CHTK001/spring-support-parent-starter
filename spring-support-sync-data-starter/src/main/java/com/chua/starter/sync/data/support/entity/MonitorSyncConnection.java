package com.chua.starter.sync.data.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 同步节点连线实体类
 *
 * @author CH
 * @since 2024/12/19
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "同步节点连线")
@Data
@TableName(value = "monitor_sync_connection")
public class MonitorSyncConnection extends SysBase {

    /**
     * 连线ID
     */
    @TableId(value = "sync_connection_id", type = IdType.AUTO)
    @Schema(description = "连线ID")
    private Long syncConnectionId;

    /**
     * 关联任务ID
     */
    @TableField(value = "sync_task_id")
    @Schema(description = "关联任务ID")
    @NotNull(message = "任务ID不能为空")
    private Long syncTaskId;

    /**
     * 源节点ID
     */
    @TableField(value = "source_node_id")
    @Schema(description = "源节点ID")
    @NotNull(message = "源节点ID不能为空")
    private Long sourceNodeId;

    /**
     * 源节点Key(前端标识)
     */
    @TableField(value = "source_node_key")
    @Schema(description = "源节点Key(前端标识)")
    @Size(max = 100, message = "源节点Key最大长度100")
    private String sourceNodeKey;

    /**
     * 源节点输出端口
     */
    @TableField(value = "source_handle")
    @Schema(description = "源节点输出端口")
    @Size(max = 50, message = "源节点输出端口最大长度50")
    private String sourceHandle;

    /**
     * 目标节点ID
     */
    @TableField(value = "target_node_id")
    @Schema(description = "目标节点ID")
    @NotNull(message = "目标节点ID不能为空")
    private Long targetNodeId;

    /**
     * 目标节点Key(前端标识)
     */
    @TableField(value = "target_node_key")
    @Schema(description = "目标节点Key(前端标识)")
    @Size(max = 100, message = "目标节点Key最大长度100")
    private String targetNodeKey;

    /**
     * 目标节点输入端口
     */
    @TableField(value = "target_handle")
    @Schema(description = "目标节点输入端口")
    @Size(max = 50, message = "目标节点输入端口最大长度50")
    private String targetHandle;

    /**
     * 连接类型: DATA/CONTROL
     */
    @TableField(value = "connection_type")
    @Schema(description = "连接类型: DATA/CONTROL")
    @Size(max = 50, message = "连接类型最大长度50")
    private String connectionType;

    /**
     * 连线标签
     */
    @TableField(value = "connection_label")
    @Schema(description = "连线标签")
    @Size(max = 100, message = "连线标签最大长度100")
    private String connectionLabel;

    /**
     * 创建时间
     */
    @TableField(value = "sync_connection_create_time")
    @Schema(description = "创建时间")
    private LocalDateTime syncConnectionCreateTime;
}
