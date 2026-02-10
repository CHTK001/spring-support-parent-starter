package com.chua.starter.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 同步节点实体类
 * <p>
 * 节点类型: INPUT(输入端), OUTPUT(输出端), FILTER(过滤器), DATA_CENTER(数据中心)
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "同步节点")
@Data
@TableName(value = "monitor_sync_node")
public class MonitorSyncNode extends SysBase {

    /**
     * 节点ID
     */
    @TableId(value = "sync_node_id", type = IdType.AUTO)
    @Schema(description = "节点ID")
    private Long syncNodeId;

    /**
     * 关联任务ID
     */
    @TableField(value = "sync_task_id")
    @Schema(description = "关联任务ID")
    @NotNull(message = "任务ID不能为空")
    private Long syncTaskId;

    /**
     * 节点类型: INPUT/OUTPUT/FILTER/DATA_CENTER/INPUT_OUTPUT/OUTPUT_INPUT
     */
    @TableField(value = "sync_node_type")
    @Schema(description = "节点类型: INPUT/OUTPUT/FILTER/DATA_CENTER/INPUT_OUTPUT/OUTPUT_INPUT")
    @NotBlank(message = "节点类型不能为空")
    @Size(max = 50, message = "节点类型最大长度50")
    private String syncNodeType;

    /**
     * SPI名称(jdbc/csv/local等)
     */
    @TableField(value = "sync_node_spi_name")
    @Schema(description = "SPI名称(jdbc/csv/local等)")
    @NotBlank(message = "SPI名称不能为空")
    @Size(max = 100, message = "SPI名称最大长度100")
    private String syncNodeSpiName;

    /**
     * 节点名称
     */
    @TableField(value = "sync_node_name")
    @Schema(description = "节点名称")
    @Size(max = 255, message = "节点名称最大长度255")
    private String syncNodeName;

    /**
     * 节点唯一标识(前端生成,用于连线)
     */
    @TableField(value = "sync_node_key")
    @Schema(description = "节点唯一标识(前端生成,用于连线)")
    @Size(max = 100, message = "节点标识最大长度100")
    private String syncNodeKey;

    /**
     * 配置参数JSON
     */
    @TableField(value = "sync_node_config")
    @Schema(description = "配置参数JSON")
    private String syncNodeConfig;

    /**
     * 前端位置信息JSON(x,y坐标)
     */
    @TableField(value = "sync_node_position")
    @Schema(description = "前端位置信息JSON(x,y坐标)")
    private String syncNodePosition;

    /**
     * 节点顺序
     */
    @TableField(value = "sync_node_order")
    @Schema(description = "节点顺序")
    private Integer syncNodeOrder;

    /**
     * 是否启用: 0否 1是
     */
    @TableField(value = "sync_node_enabled")
    @Schema(description = "是否启用: 0否 1是")
    private Integer syncNodeEnabled;

    /**
     * 节点描述
     */
    @TableField(value = "sync_node_desc")
    @Schema(description = "节点描述")
    @Size(max = 500, message = "节点描述最大长度500")
    private String syncNodeDesc;

    /**
     * 创建时间
     */
    @TableField(value = "sync_node_create_time")
    @Schema(description = "创建时间")
    private LocalDateTime syncNodeCreateTime;

    /**
     * 更新时间
     */
    @TableField(value = "sync_node_update_time")
    @Schema(description = "更新时间")
    private LocalDateTime syncNodeUpdateTime;
}
