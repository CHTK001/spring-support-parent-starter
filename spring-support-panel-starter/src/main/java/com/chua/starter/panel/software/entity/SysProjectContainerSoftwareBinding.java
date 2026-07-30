package com.chua.starter.panel.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

@Data
@TableName("sys_project_container_software_binding")
public class SysProjectContainerSoftwareBinding extends SysBase {

    @TableId(value = "sys_project_container_software_binding_id", type = IdType.AUTO)
    private Integer sysProjectContainerSoftwareBindingId;
    private Integer sysProjectId;
    private Integer sysProjectContainerSoftwarePresetId;
    private String softwareCode;
    private String softwareName;
    private String serviceName;
    private String softwareCategory;
    private String softwareVersion;
    private String softwareIcon;
    private String softwareVendor;
    private String osType;
    private String softwareArch;
    private String installPath;
    private String exposePorts;
    private Integer hasPortMapping;
    private String installScript;
    private String startScript;
    private String stopScript;
    private String restartScript;
    private String statusScript;
    private String uninstallScript;
    private String installStatus;
    private String runtimeStatus;
    private Integer bindingCustom;
    private Integer bindingDelete;
    private Integer bindingStatus;
    private Integer bindingSort;
    private String bindingRemark;
    private String systemEnvVars;
    private String dockerEnvVars;
}
