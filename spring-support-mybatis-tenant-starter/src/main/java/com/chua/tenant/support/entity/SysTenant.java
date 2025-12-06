package com.chua.tenant.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 租户�?
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "租户�?)
@Schema(description = "租户�?)
@Data
@TableName(value = "sys_tenant")
public class SysTenant extends SysBase {

    /**
     * 租户ID
     */
    @TableId(value = "sys_tenant_id", type = IdType.AUTO)
    @ApiModelProperty(value = "租户ID")
    @Schema(description = "租户ID")
    private Integer sysTenantId;

    /**
     * 名称
     */
    @TableField(value = "sys_tenant_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    private String sysTenantName;

    /**
     * 账号
     */
    @TableField(value = "sys_tenant_username")
    @ApiModelProperty(value = "账号")
    @Schema(description = "账号")
    private String sysTenantUsername;

    /**
     * 密码
     */
    @TableField(value = "sys_tenant_password")
    @ApiModelProperty(value = "密码")
    @Schema(description = "密码")
    private String sysTenantPassword;

    /**
     * 签名
     */
    @TableField(value = "sys_tenant_sign")
    @ApiModelProperty(value = "签名")
    @Schema(description = "签名")
    private String sysTenantSign;

    /**
     * 公司
     */
    @TableField(value = "sys_tenant_corporation")
    @ApiModelProperty(value = "公司")
    @Schema(description = "公司")
    private String sysTenantCorporation;

    /**
     * 是否删除; 0:正常
     */
    @TableField(value = "sys_tenant_delete")
    @ApiModelProperty(value = "是否删除; 0:正常")
    @Schema(description = "是否删除; 0:正常")
    @TableLogic(value = "0", delval = "1")
    private Integer sysTenantDelete;

    /**
     * 是否禁用; 0:正常
     */
    @TableField(value = "sys_tenant_status")
    @ApiModelProperty(value = "是否禁用; 0:正常")
    @Schema(description = "是否禁用; 0:正常")
    private Integer sysTenantStatus;

    /**
     * 联系�?
     */
    @TableField(value = "sys_tenant_contact")
    @ApiModelProperty(value = "联系�?)
    @Schema(description = "联系�?)
    private String sysTenantContact;

    /**
     * 手机�?
     */
    @TableField(value = "sys_tenant_phone")
    @ApiModelProperty(value = "手机�?)
    @Schema(description = "手机�?)
    private String sysTenantPhone;

    /**
     * 地址
     */
    @TableField(value = "sys_tenant_address")
    @ApiModelProperty(value = "地址")
    @Schema(description = "地址")
    private String sysTenantAddress;

    /**
     * 邮件
     */
    @TableField(value = "sys_tenant_email")
    @ApiModelProperty(value = "邮件")
    @Schema(description = "邮件")
    private String sysTenantEmail;

    /**
     * 唯一编码
     */
    @TableField(value = "sys_tenant_code")
    @ApiModelProperty(value = "唯一编码")
    @Schema(description = "唯一编码")
    private String sysTenantCode;

    /**
     * 租户对于配置唯一ID
     */
    @TableField(value = "sys_tenant_gid")
    @ApiModelProperty(value = "租户对于配置唯一ID")
    @Schema(description = "租户对于配置唯一ID")
    private String sysTenantGid;

    /**
     * 租户访问地址
     */
    @TableField(value = "sys_tenant_home_url")
    @ApiModelProperty(value = "租户访问地址")
    @Schema(description = "租户访问地址")
    private String sysTenantHomeUrl;

    /**
     * 备注
     */
    @TableField(value = "sys_tenant_remark")
    @ApiModelProperty(value = "备注")
    @Schema(description = "备注")
    private String sysTenantRemark;

    /**
     * 租户 - 服务ID列表
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "租户 - 服务ID列表")
    @Schema(description = "租户 - 服务ID列表")
    private List<Integer> serviceIds;

    /**
     * 租户 - 服务
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "租户 - 服务")
    @Schema(description = "租户 - 服务")
    private List<SysTenantService> sysTenantService;
}
