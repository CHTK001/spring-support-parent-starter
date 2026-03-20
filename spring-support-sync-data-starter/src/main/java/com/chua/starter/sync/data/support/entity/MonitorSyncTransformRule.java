package com.chua.starter.sync.data.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数据转换规则实体类
 *
 * @author System
 * @since 2026/03/09
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据转换规则")
@Data
@TableName(
        value = "monitor_sync_transform_rule",
        excludeProperty = {"createName", "createBy", "createTime", "updateName", "updateBy", "updateTime"}
)
public class MonitorSyncTransformRule extends SysBase {

    @TableId(value = "rule_id", type = IdType.AUTO)
    @Schema(description = "规则ID")
    private Long ruleId;

    @TableField(value = "rule_name")
    @Schema(description = "规则名称")
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 255, message = "规则名称最大长度255")
    private String ruleName;

    @TableField(value = "rule_type")
    @Schema(description = "规则类型: MAPPING/FILTER/MASKING/SCRIPT")
    @NotBlank(message = "规则类型不能为空")
    @Size(max = 50, message = "规则类型最大长度50")
    private String ruleType;

    @TableField(value = "rule_config")
    @Schema(description = "规则配置JSON")
    @NotBlank(message = "规则配置不能为空")
    private String ruleConfig;

    @TableField(value = "rule_desc")
    @Schema(description = "规则描述")
    @Size(max = 500, message = "规则描述最大长度500")
    private String ruleDesc;

    @TableField(value = "create_time")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
