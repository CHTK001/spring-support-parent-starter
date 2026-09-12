package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务器分组实体
 * <p>
 * 对应表 {@code ms_server_group}，树形结构组织主机资产。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "服务器分组")
@TableName("ms_server_group")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsServerGroup extends SysBase {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 父分组 ID（0 表示根）
     */
    @TableField("ms_server_group_parent_id")
    @Schema(description = "父分组ID,0为根", example = "0")
    private Long msServerGroupParentId;

    /**
     * 分组名称
     */
    @TableField("ms_server_group_name")
    @Schema(description = "分组名称", example = "生产环境")
    private String msServerGroupName;

    /**
     * 排序号
     */
    @TableField("ms_server_group_sort")
    @Schema(description = "排序号", example = "1")
    private Integer msServerGroupSort;

    /**
     * 备注
     */
    @TableField("ms_server_group_remark")
    @Schema(description = "备注", example = "生产环境服务器")
    private String msServerGroupRemark;

    /**
     * 分组颜色（十六进制，如 #6d5dfc），用于标记/徽标底色
     */
    @TableField("ms_server_group_color")
    @Schema(description = "分组颜色(十六进制)", example = "#6d5dfc")
    private String msServerGroupColor;
}
