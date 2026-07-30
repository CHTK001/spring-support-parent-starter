package com.chua.starter.panel.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

@Data
@TableName("sys_project_container_software_preset")
public class SysProjectContainerSoftwarePreset extends SysBase {

    @TableId(value = "sys_project_container_software_preset_id", type = IdType.AUTO)
    private Integer sysProjectContainerSoftwarePresetId;
    private String softwareCode;
    private String serviceName;
    private String softwareName;
    private String softwareCategory;
    private String presetType;
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
    private Integer presetBuiltin;
    private Integer presetDelete;
    private Integer presetStatus;
    private Integer presetSort;
    private String presetRemark;
    private String systemEnvVars;
    private String dockerEnvVars;
}
