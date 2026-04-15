package com.chua.starter.soft.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.chua.starter.mybatis.type.EncryptTypeHandler;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("仓库来源子表")
@TableName(value = "soft_repository_source", autoResultMap = true)
public class SoftRepositorySourceEntity extends SysBase {

    @TableId(value = "soft_repository_source_id", type = IdType.AUTO)
    @ApiModelProperty("来源ID")
    private Integer softRepositorySourceId;

    @ApiModelProperty("仓库ID")
    private Integer softRepositoryId;

    @ApiModelProperty("来源名称")
    private String sourceName;

    @ApiModelProperty("来源分类")
    private String sourceKind;

    @ApiModelProperty("来源类型")
    private String sourceType;

    @ApiModelProperty("来源地址")
    private String sourceUrl;

    @ApiModelProperty("本地目录")
    private String localDirectory;

    @ApiModelProperty("认证类型")
    private String authType;

    @ApiModelProperty("用户名")
    private String username;

    @TableField(typeHandler = EncryptTypeHandler.class)
    @ApiModelProperty("密码")
    private String password;

    @TableField(typeHandler = EncryptTypeHandler.class)
    @ApiModelProperty("令牌")
    private String token;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("排序")
    private Integer sortOrder;

    @ApiModelProperty("附加配置")
    private String sourceConfig;
}

