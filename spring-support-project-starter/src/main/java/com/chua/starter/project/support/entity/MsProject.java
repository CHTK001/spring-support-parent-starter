package com.chua.starter.project.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 发布项目实体
 * <p>
 * 对应表 {@code ms_project}，存储部署项目台账主数据，包含目标服务器、
 * 部署目录、启停脚本、升级策略与当前运行时状态等配置。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "发布项目")
@TableName("ms_project")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsProject extends SysBase {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 项目名称
     */
    @TableField("ms_project_name")
    @Schema(description = "项目名称", example = "user-service")
    private String msProjectName;

    /**
     * 项目编码（唯一标识）
     */
    @TableField("ms_project_code")
    @Schema(description = "项目编码", example = "user-service")
    private String msProjectCode;

    /**
     * 项目类型：SPRINGBOOT_MONO / SPRINGBOOT_WAR / SPRINGBOOT_SPLIT / NODEJS / STATIC / CUSTOM
     */
    @TableField("ms_project_type")
    @Schema(description = "项目类型", example = "SPRINGBOOT_MONO")
    private String msProjectType;

    /**
     * 目标服务器主机 ID 列表（JSON 数组，如 [1,2,3]，要求同 OS）
     */
    @TableField("ms_project_host_ids")
    @Schema(description = "目标服务器ID列表(JSON数组)", example = "[1,2,3]")
    private String msProjectHostIds;

    /**
     * 目标主机操作系统类型（冗余字段，校验所有主机同类型）：LINUX / WINDOWS
     */
    @TableField("ms_project_host_os")
    @Schema(description = "目标主机OS类型", example = "LINUX")
    private String msProjectHostOs;

    /**
     * 部署目录
     */
    @TableField("ms_project_deploy_dir")
    @Schema(description = "部署目录", example = "/opt/apps/user-service")
    private String msProjectDeployDir;

    /**
     * 启动脚本
     */
    @TableField("ms_project_start_script")
    @Schema(description = "启动脚本")
    private String msProjectStartScript;

    /**
     * 停止脚本
     */
    @TableField("ms_project_stop_script")
    @Schema(description = "停止脚本")
    private String msProjectStopScript;

    /**
     * 重启脚本
     */
    @TableField("ms_project_restart_script")
    @Schema(description = "重启脚本")
    private String msProjectRestartScript;

    /**
     * 升级前是否先删除旧内容：0=否 1=是
     */
    @TableField("ms_project_delete_before")
    @Schema(description = "升级前是否删除旧内容", example = "0")
    private Integer msProjectDeleteBefore;

    /**
     * 覆盖模式：OVERWRITE=直接覆盖（保留其余文件） / REPLACE=全量替换（清空后部署）
     */
    @TableField("ms_project_overwrite_mode")
    @Schema(description = "覆盖模式: OVERWRITE/REPLACE", example = "OVERWRITE")
    private String msProjectOverwriteMode;

    /**
     * 是否自动解压：0=否（仅上传） 1=是（按扩展名解压）
     */
    @TableField("ms_project_auto_extract")
    @Schema(description = "是否自动解压", example = "1")
    private Integer msProjectAutoExtract;

    /**
     * 当前版本（最近一次部署包文件名/版本号，升级时更新）
     */
    @TableField("ms_project_current_version")
    @Schema(description = "当前版本")
    private String msProjectCurrentVersion;

    /**
     * 运行时状态：UNKNOWN/RUNNING/STOPPED/DEPLOYING/UPGRADING/RESTARTING
     */
    @TableField("ms_project_runtime_status")
    @Schema(description = "运行时状态", example = "STOPPED")
    private String msProjectRuntimeStatus;

    /**
     * 启用状态：0=停用 1=启用
     */
    @TableField("ms_project_enabled")
    @Schema(description = "启用状态", example = "1")
    private Integer msProjectEnabled;

    /**
     * 排序
     */
    @TableField("ms_project_sort")
    @Schema(description = "排序", example = "100")
    private Integer msProjectSort;

    /**
     * 备注
     */
    @TableField("ms_project_remark")
    @Schema(description = "备注")
    private String msProjectRemark;
}