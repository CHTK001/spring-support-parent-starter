package com.chua.starter.project.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 部署日志实体
 * <p>
 * 对应表 {@code ms_project_deploy_log}，记录部署、升级、启停等操作的执行结果。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "部署日志")
@TableName("ms_project_deploy_log")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsProjectDeployLog extends SysBase {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 主键
    */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
     * 关联项目 标识
     */
    @TableField("ms_project_deploy_log_project_id")
    @Schema(description = "项目ID", example = "1")
    private Long msProjectDeployLogProjectId; // msprojectdeploy日志projectid

    /**
     * 项目名称（冗余）
     */
    @TableField("ms_project_deploy_log_project_name")
    @Schema(description = "项目名称", example = "user-service")
    private String msProjectDeployLogProjectName; // msprojectdeploy日志project名称

    /**
     * 目标主机 标识
     */
    @TableField("ms_project_deploy_log_host_id")
    @Schema(description = "目标主机ID", example = "1")
    private Long msProjectDeployLogHostId; // msprojectdeploy日志主机标识

    /**
     * 主机名称（冗余）
     */
    @TableField("ms_project_deploy_log_host_name")
    @Schema(description = "主机名称", example = "应用服务器-01")
    private String msProjectDeployLogHostName; // msprojectdeploy日志主机名称

    /**
     * 操作类型：DEPLOY/UPGRADE/启动/停止/RESTART
     */
    @TableField("ms_project_deploy_log_action")
    @Schema(description = "操作类型", example = "DEPLOY")
    private String msProjectDeployLogAction; // msprojectdeploy日志动作

    /**
     * 上传版本标识（部署包文件名或版本号）
     */
    @TableField("ms_project_deploy_log_version")
    @Schema(description = "版本标识")
    private String msProjectDeployLogVersion; // msprojectdeploy日志版本

    /**
     * 执行状态：成功/失败/RUNNING
     */
    @TableField("ms_project_deploy_log_status")
    @Schema(description = "执行状态", example = "SUCCESS")
    private String msProjectDeployLogStatus; // msprojectdeploy日志状态

    /**
     * 执行输出（标准输出截断存储）
     */
    @TableField("ms_project_deploy_log_output")
    @Schema(description = "执行输出")
    private String msProjectDeployLogOutput; // msprojectdeploy日志输出

    /**
     * 错误信息
     */
    @TableField("ms_project_deploy_log_error")
    @Schema(description = "错误信息")
    private String msProjectDeployLogError; // msprojectdeploy日志错误

    /**
     * 开始时间
     */
    @TableField("ms_project_deploy_log_start_time")
    @Schema(description = "开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime msProjectDeployLogStartTime; // msprojectdeploy日志启动时间

    /**
     * 结束时间
     */
    @TableField("ms_project_deploy_log_end_time")
    @Schema(description = "结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime msProjectDeployLogEndTime; // msprojectdeploy日志结束时间

    /**
     * 耗时（毫秒）
     */
    @TableField("ms_project_deploy_log_cost_ms")
    @Schema(description = "耗时(ms)")
    private Long msProjectDeployLogCostMs; // msprojectdeploy日志costms
}
