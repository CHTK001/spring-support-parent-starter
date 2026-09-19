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

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
     * 项目名称
     */
    @TableField("ms_project_name")
    @Schema(description = "项目名称", example = "user-service")
    private String msProjectName; // msproject名称

    /**
     * 项目编码（唯一标识）
     */
    @TableField("ms_project_code")
    @Schema(description = "项目编码", example = "user-service")
    private String msProjectCode; // msproject编码

    /**
     * 项目类型：SPRINGBOOT_MONO / SPRINGBOOT_WAR / SPRINGBOOT_分割 / NODEJS / 静态 / 习俗
     */
    @TableField("ms_project_type")
    @Schema(description = "项目类型", example = "SPRINGBOOT_MONO")
    private String msProjectType; // msproject类型

    /**
     * 目标服务器主机 标识 列表（JSON 数组，如 [1,2,3]，要求同 OS）
     */
    @TableField("ms_project_host_ids")
    @Schema(description = "目标服务器ID列表(JSON数组)", example = "[1,2,3]")
    private String msProjectHostIds; // msproject主机标识

    /**
     * 目标主机操作系统类型（冗余字段，校验所有主机同类型）：Linux / 窗口
     */
    @TableField("ms_project_host_os")
    @Schema(description = "目标主机OS类型", example = "LINUX")
    private String msProjectHostOs; // msproject主机os

    /**
     * 部署目录
     */
    @TableField("ms_project_deploy_dir")
    @Schema(description = "部署目录", example = "/opt/apps/user-service")
    private String msProjectDeployDir; // msprojectdeploydir

    /**
     * 启动脚本
     */
    @TableField("ms_project_start_script")
    @Schema(description = "启动脚本")
    private String msProjectStartScript; // msproject启动script

    /**
     * 停止脚本
     */
    @TableField("ms_project_stop_script")
    @Schema(description = "停止脚本")
    private String msProjectStopScript; // msproject停止script

    /**
     * 重启脚本
     */
    @TableField("ms_project_restart_script")
    @Schema(description = "重启脚本")
    private String msProjectRestartScript; // msprojectrestartscript

    /**
     * 日志目录（实时日志跟踪）
     */
    @TableField("ms_project_log_dir")
    @Schema(description = "日志目录", example = "/opt/apps/user-service/logs")
    private String msProjectLogDir; // msproject日志dir

    /**
     * 日志文件名（如 app.日志）
     */
    @TableField("ms_project_log_file")
    @Schema(description = "日志文件名", example = "app.log")
    private String msProjectLogFile; // msproject日志文件

    /**
     * 升级前是否先删除旧内容：0=否 1=是
     */
    @TableField("ms_project_delete_before")
    @Schema(description = "升级前是否删除旧内容", example = "0")
    private Integer msProjectDeleteBefore; // msproject删除之前

    /**
     * 覆盖模式：OVERWRITE=直接覆盖（保留其余文件） / 替换=全量替换（清空后部署）
     */
    @TableField("ms_project_overwrite_mode")
    @Schema(description = "覆盖模式: OVERWRITE/REPLACE", example = "OVERWRITE")
    private String msProjectOverwriteMode; // msprojectoverwritemode

    /**
     * 是否自动解压：0=否（仅上传） 1=是（按扩展名解压）
     */
    @TableField("ms_project_auto_extract")
    @Schema(description = "是否自动解压", example = "1")
    private Integer msProjectAutoExtract; // msprojectautoextract

    /**
     * 部署成功后是否自动执行启动脚本：0=否 1=是
     */
    @TableField("ms_project_auto_start")
    @Schema(description = "部署成功后自动启动", example = "0")
    private Integer msProjectAutoStart;

    /**
     * 部署后健康检查：0=关闭 1=开启
     */
    @TableField("ms_project_health_enabled")
    @Schema(description = "部署后健康检查", example = "0")
    private Integer msProjectHealthEnabled;

    /**
     * 健康检查类型：PORT / HTTP / PROCESS
     */
    @TableField("ms_project_health_type")
    @Schema(description = "健康检查类型", example = "PORT")
    private String msProjectHealthType;

    /**
     * 健康检查目标（端口号 / URL / 进程关键字）
     */
    @TableField("ms_project_health_target")
    @Schema(description = "健康检查目标", example = "8080")
    private String msProjectHealthTarget;

    /**
     * 健康检查超时（秒）
     */
    @TableField("ms_project_health_timeout")
    @Schema(description = "健康检查超时(秒)", example = "30")
    private Integer msProjectHealthTimeout;

    /**
     * 部署失败自动回滚：0=关闭 1=开启
     */
    @TableField("ms_project_auto_rollback")
    @Schema(description = "失败自动回滚", example = "0")
    private Integer msProjectAutoRollback;

    /**
     * 单台部署失败重试次数
     */
    @TableField("ms_project_deploy_retry")
    @Schema(description = "单台失败重试次数", example = "0")
    private Integer msProjectDeployRetry;

    /**
     * 归档保留数量（0=不清理，超出自动清理旧版本）
     */
    @TableField("ms_project_archive_keep")
    @Schema(description = "归档保留数量", example = "0")
    private Integer msProjectArchiveKeep; // msprojectauto启动

    /**
     * 守护进程开关：0=关闭 1=开启（持续存活检测 + 挂了自动拉起）
     */
    @TableField("ms_project_watchdog_enabled")
    @Schema(description = "守护进程开关", example = "0")
    private Integer msProjectWatchdogEnabled;

    /**
     * 守护检测间隔（秒，每个项目独立设置，最小 5 秒）
     */
    @TableField("ms_project_watchdog_interval")
    @Schema(description = "守护检测间隔(秒)", example = "30")
    private Integer msProjectWatchdogInterval;

    /**
     * 守护连续失败阈值：连续 N 次检测不健康才触发自动重启（0=不自动重启仅告警，1=立即重启）
     */
    @TableField("ms_project_watchdog_max_fail")
    @Schema(description = "守护连续失败阈值", example = "1")
    private Integer msProjectWatchdogMaxFail;

    /**
     * 自定义状态检测脚本（exit 0=健康；为空时回退健康检查类型探测）
     */
    @TableField("ms_project_check_script")
    @Schema(description = "自定义状态检测脚本")
    private String msProjectCheckScript;

    /**
     * 部署前置门禁：0=关闭 1=开启（部署前校验目标主机在线状态与延迟，不可达主机直接拦截）
     */
    @TableField("ms_project_preflight_gate")
    @Schema(description = "部署前置门禁开关", example = "1")
    private Integer msProjectPreflightGate;

    /**
     * 指标采集开关：0=关闭 1=开启（SSH 轮询采集进程 CPU/内存/端口存活）
     */
    @TableField("ms_project_metric_enabled")
    @Schema(description = "指标采集开关", example = "0")
    private Integer msProjectMetricEnabled;

    /**
     * 指标采集间隔（秒，每个项目独立设置，最小 5 秒）
     */
    @TableField("ms_project_metric_interval")
    @Schema(description = "指标采集间隔(秒)", example = "60")
    private Integer msProjectMetricInterval;

    /**
     * 指标采集目标主机 标识（JSON 数组，如 [1,2]；为空时采集全部绑定主机）
     */
    @TableField("ms_project_metric_host_ids")
    @Schema(description = "指标采集目标主机ID列表(JSON数组)")
    private String msProjectMetricHostIds;

    /**
     * 当前版本（最近一次部署包文件名/版本号，升级时更新）
     */
    @TableField("ms_project_current_version")
    @Schema(description = "当前版本")
    private String msProjectCurrentVersion; // msproject当前版本

    /**
     * 运行时状态：UNKNOWN/RUNNING/STOPPED/DEPLOYING/UPGRADING/RESTARTING
     */
    @TableField("ms_project_runtime_status")
    @Schema(description = "运行时状态", example = "STOPPED")
    private String msProjectRuntimeStatus; // msprojectruntime状态

    /**
     * 启用状态：0=停用 1=启用
     */
    @TableField("ms_project_enabled")
    @Schema(description = "启用状态", example = "1")
    private Integer msProjectEnabled; // msproject已启用

    /**
     * 排序
     */
    @TableField("ms_project_sort")
    @Schema(description = "排序", example = "100")
    private Integer msProjectSort; // msproject排序

    /**
     * 备注
     */
    @TableField("ms_project_remark")
    @Schema(description = "备注")
    private String msProjectRemark; // msproject评论
}
