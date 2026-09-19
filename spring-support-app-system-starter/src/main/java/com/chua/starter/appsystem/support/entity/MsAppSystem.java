package com.chua.starter.appsystem.support.entity;

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
 * 业务系统实体
 * <p>
 * 对应表 {@code ms_app_system}，是「立项」层台账：一个业务系统包含多个部署项目，
 * 并持有自己的主机池，界定项目可选的目标主机范围。
 * {@code msAppSystemType} 只是操作系统的展示标签，不参与主机过滤与脚本选型。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "业务系统")
@TableName("ms_app_system")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsAppSystem extends SysBase {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 系统名称
     */
    @TableField("ms_app_system_name")
    @Schema(description = "系统名称", example = "交易中心")
    private String msAppSystemName;

    /**
     * 系统编码（全局唯一）
     */
    @TableField("ms_app_system_code")
    @Schema(description = "系统编码", example = "trade-center")
    private String msAppSystemCode;

    /**
     * 操作系统标签：LINUX / WINDOWS（仅用于展示与归类，不约束主机与脚本）
     */
    @TableField("ms_app_system_type")
    @Schema(description = "操作系统标签", example = "LINUX")
    private String msAppSystemType;

    /**
     * 主机池（JSON 数组，如 [1,2,3]，取值为 ms_server_host 主键）
     */
    @TableField("ms_app_system_host_ids")
    @Schema(description = "主机池ID列表(JSON数组)", example = "[1,2,3]")
    private String msAppSystemHostIds;

    /**
     * 启用状态：0=停用 1=启用
     */
    @TableField("ms_app_system_enabled")
    @Schema(description = "启用状态", example = "1")
    private Integer msAppSystemEnabled;

    /**
     * 排序
     */
    @TableField("ms_app_system_sort")
    @Schema(description = "排序", example = "100")
    private Integer msAppSystemSort;

    /**
     * 备注
     */
    @TableField("ms_app_system_remark")
    @Schema(description = "备注")
    private String msAppSystemRemark;
}
