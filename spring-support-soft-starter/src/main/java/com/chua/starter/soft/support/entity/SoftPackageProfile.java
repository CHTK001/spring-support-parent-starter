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
@ApiModel("软件画像")
@TableName("soft_package_profile")
public class SoftPackageProfile extends SysBase {

    @TableId(value = "soft_package_profile_id", type = IdType.AUTO)
    @ApiModelProperty("画像ID")
    private Integer softPackageProfileId;

    @ApiModelProperty("画像编码")
    private String profileCode;

    @ApiModelProperty("画像名称")
    private String profileName;

    @ApiModelProperty("分类")
    private String packageCategory;

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("是否内置")
    private Boolean builtin;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("扩展元数据")
    private String metadataJson;
}
