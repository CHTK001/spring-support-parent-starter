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
@ApiModel("软件画像模板")
@TableName("soft_package_profile_template")
public class SoftPackageProfileTemplate extends SysBase {

    @TableId(value = "soft_package_profile_template_id", type = IdType.AUTO)
    @ApiModelProperty("模板ID")
    private Integer softPackageProfileTemplateId;

    @ApiModelProperty("画像ID")
    private Integer softPackageProfileId;

    @ApiModelProperty("模板作用域")
    private String templateScope;

    @ApiModelProperty("模板编码")
    private String templateCode;

    @ApiModelProperty("模板名称")
    private String templateName;

    @ApiModelProperty("模板路径")
    private String templatePath;

    @ApiModelProperty("模板引擎")
    private String templateEngine;

    @ApiModelProperty("模板内容")
    private String templateContent;

    @ApiModelProperty("排序")
    private Integer sortOrder;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("扩展元数据")
    private String metadataJson;
}
