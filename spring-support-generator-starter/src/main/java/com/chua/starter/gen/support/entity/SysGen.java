package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @author CH
 */
@ApiModel(description = "sys_gen")
@Schema
@Data
@TableName(value = "sys_gen")
public class SysGen implements Serializable {
    /**
     * 主键
     */
    @TableId(value = "gen_id", type = IdType.INPUT)
    @ApiModelProperty(value = "主键")
    @Schema(description = "主键")
    private Integer genId;

    /**
     * 名称
     */
    @TableField(value = "gen_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    private String genName;

    /**
     * url
     */
    @TableField(value = "gen_url")
    @ApiModelProperty(value = "url")
    @Schema(description = "url")
    private String genUrl;

    /**
     * 用户名
     */
    @TableField(value = "gen_user")
    @ApiModelProperty(value = "用户名")
    @Schema(description = "用户名")
    private String genUser;

    /**
     * 密码
     */
    @TableField(value = "gen_password")
    @ApiModelProperty(value = "密码")
    @Schema(description = "密码")
    private String genPassword;

    /**
     * 驱动包
     */
    @TableField(value = "gen_driver")
    @ApiModelProperty(value = "驱动包")
    @Schema(description = "驱动包")
    private String genDriver;

    private static final long serialVersionUID = 1L;
}