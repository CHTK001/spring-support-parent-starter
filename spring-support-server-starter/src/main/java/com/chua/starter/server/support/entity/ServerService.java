package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("服务器服务")
@TableName("server_service")
public class ServerService extends SysBase {

    @TableId(value = "server_service_id", type = IdType.AUTO)
    @ApiModelProperty("服务器服务ID")
    private Integer serverServiceId;

    @TableField("server_id")
    @ApiModelProperty("服务器ID")
    private Integer serverId;

    @TableField("server_service_code")
    @ApiModelProperty("服务编码")
    private String serviceCode;

    @TableField("server_service_name")
    @ApiModelProperty("服务名称")
    private String serviceName;

    @TableField("server_service_type")
    @ApiModelProperty("服务类型")
    private String serviceType;

    @TableField("server_soft_package_id")
    @ApiModelProperty("关联软件ID")
    private Integer softPackageId;

    @TableField("server_soft_package_version_id")
    @ApiModelProperty("关联软件版本ID")
    private Integer softPackageVersionId;

    @TableField("server_soft_installation_id")
    @ApiModelProperty("关联软件安装ID")
    private Integer softInstallationId;

    @TableField("server_install_path")
    @ApiModelProperty("安装目录")
    private String installPath;

    @TableField("server_runtime_status")
    @ApiModelProperty("运行状态")
    private String runtimeStatus;

    @TableField("server_config_paths_json")
    @ApiModelProperty("配置路径JSON")
    private String configPathsJson;

    @TableField("server_log_paths_json")
    @ApiModelProperty("日志路径JSON")
    private String logPathsJson;

    @TableField("server_config_template")
    @ApiModelProperty("配置模板")
    private String configTemplate;

    @TableField("server_init_script")
    @ApiModelProperty("初始化脚本")
    private String initScript;

    @TableField("server_install_script")
    @ApiModelProperty("安装脚本")
    private String installScript;

    @TableField("server_uninstall_script")
    @ApiModelProperty("卸载脚本")
    private String uninstallScript;

    @TableField("server_detect_script")
    @ApiModelProperty("检测脚本")
    private String detectScript;

    @TableField("server_register_script")
    @ApiModelProperty("注册脚本")
    private String registerScript;

    @TableField("server_unregister_script")
    @ApiModelProperty("取消注册脚本")
    private String unregisterScript;

    @TableField("server_start_script")
    @ApiModelProperty("启动脚本")
    private String startScript;

    @TableField("server_stop_script")
    @ApiModelProperty("停止脚本")
    private String stopScript;

    @TableField("server_restart_script")
    @ApiModelProperty("重启脚本")
    private String restartScript;

    @TableField("server_status_script")
    @ApiModelProperty("状态脚本")
    private String statusScript;

    @TableField("server_enabled")
    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @TableField("server_description")
    @ApiModelProperty("描述")
    private String description;

    @TableField("server_metadata_json")
    @ApiModelProperty("扩展元数据")
    private String metadataJson;

    @TableField("server_last_operation_time")
    @ApiModelProperty("最后操作时间")
    private LocalDateTime lastOperationTime;

    @TableField("server_last_operation_message")
    @ApiModelProperty("最后操作说明")
    private String lastOperationMessage;

    @TableField(exist = false)
    @ApiModelProperty("服务器名称")
    private String serverName;

    @TableField(exist = false)
    @ApiModelProperty("主机地址")
    private String host;

    @TableField(exist = false)
    @ApiModelProperty("最近操作日志ID")
    private Integer latestOperationLogId;

    @TableField(exist = false)
    @ApiModelProperty("最近操作类型")
    private String latestOperationType;

    @TableField(exist = false)
    @ApiModelProperty("最近操作是否成功")
    private Boolean latestOperationSuccess;

    @TableField(exist = false)
    @ApiModelProperty("最近操作输出")
    private String latestOperationOutput;

    @TableField(exist = false)
    @ApiModelProperty("最近AI失败原因")
    private String latestAiReason;

    @TableField(exist = false)
    @ApiModelProperty("最近AI处理方案")
    private String latestAiSolution;

    @TableField(exist = false)
    @ApiModelProperty("最近AI修复脚本")
    private String latestAiFixScript;

    @TableField(exist = false)
    @ApiModelProperty("最近AI提供商")
    private String latestAiProvider;

    @TableField(exist = false)
    @ApiModelProperty("最近AI模型")
    private String latestAiModel;

    @TableField(exist = false)
    @ApiModelProperty("最近知识库ID")
    private Integer latestKnowledgeId;
}
