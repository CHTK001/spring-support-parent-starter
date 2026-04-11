package com.chua.starter.soft.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("配置快照")
@TableName("soft_config_snapshot")
public class SoftConfigSnapshot extends SysBase {

    @TableId(value = "soft_config_snapshot_id", type = IdType.AUTO)
    @ApiModelProperty("快照ID")
    private Integer softConfigSnapshotId;

    @ApiModelProperty("安装ID")
    private Integer softInstallationId;

    @ApiModelProperty("配置路径")
    private String configPath;

    @ApiModelProperty("快照名称")
    private String snapshotName;

    @ApiModelProperty("配置内容")
    private String configContent;

    @ApiModelProperty("备注")
    private String operationRemark;
}
