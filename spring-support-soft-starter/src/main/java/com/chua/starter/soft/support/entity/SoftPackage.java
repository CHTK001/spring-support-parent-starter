package com.chua.starter.soft.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("软件主档")
@TableName("soft_package")
public class SoftPackage extends SysBase {

    @TableId(value = "soft_package_id", type = IdType.AUTO)
    @ApiModelProperty("软件ID")
    private Integer softPackageId;

    @ApiModelProperty("仓库ID")
    private Integer softRepositoryId;

    @ApiModelProperty("软件画像ID")
    private Integer softPackageProfileId;

    @ApiModelProperty("软件编码")
    private String packageCode;

    @ApiModelProperty("软件画像编码")
    private String profileCode;

    @ApiModelProperty("软件名称")
    private String packageName;

    @ApiModelProperty("分类")
    private String packageCategory;

    @ApiModelProperty("操作系统")
    private String osType;

    @ApiModelProperty("架构")
    private String architecture;

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("图标")
    private String iconUrl;

    @TableField(exist = false)
    @ApiModelProperty("软件聚合键")
    private String softwareKey;
}
