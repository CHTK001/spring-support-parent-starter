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
@ApiModel("软件画像字段")
@TableName("soft_package_profile_field")
public class SoftPackageProfileField extends SysBase {

    @TableId(value = "soft_package_profile_field_id", type = IdType.AUTO)
    @ApiModelProperty("字段ID")
    private Integer softPackageProfileFieldId;

    @ApiModelProperty("画像ID")
    private Integer softPackageProfileId;

    @ApiModelProperty("字段编码")
    private String fieldKey;

    @ApiModelProperty("字段名称")
    private String fieldLabel;

    @ApiModelProperty("字段作用域")
    private String fieldScope;

    @ApiModelProperty("组件类型")
    private String componentType;

    @ApiModelProperty("分组名称")
    private String groupName;

    @ApiModelProperty("字段说明")
    private String fieldDescription;

    @ApiModelProperty("排序")
    private Integer sortOrder;

    @ApiModelProperty("是否必填")
    private Boolean requiredFlag;

    @ApiModelProperty("默认值")
    private String defaultValue;

    @ApiModelProperty("选项JSON")
    private String optionsJson;

    @ApiModelProperty("校验JSON")
    private String validationJson;

    @ApiModelProperty("显示条件JSON")
    private String conditionJson;

    @ApiModelProperty("渲染目标")
    private String targetPath;

    @ApiModelProperty("扩展元数据")
    private String metadataJson;
}
