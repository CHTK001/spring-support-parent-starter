package com.chua.starter.sso.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.chua.common.support.base.validator.group.AddGroup;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 用户登录视图对象
 *
 * @since 2024/8/7
 * @author CH
 */
@Schema(description = "当前登录用户视图对象")
@Data
public class UserInfoVO {

    /**
     * 用户标识
     */
    @TableId(value = "sys_user_id", type = IdType.AUTO)
    @ApiModelProperty(value = "用户ID")
    @Schema(description = "用户ID")
    @NotNull(message = "不能为null")
    /**
     * SYS用户标识
    */
    private Integer sysUserId;

    /**
     * 账号
     */
    @TableField(value = "sys_user_username")
    @ApiModelProperty(value = "账号")
    @Schema(description = "账号")
    @Size(max = 255, message = "账号最大长度要小于 255")
    @NotEmpty(message = "账号不能为空", groups = {AddGroup.class})
    /**
     * SYS用户用户名
    */
    private String sysUserUsername;

    /**
     * 名称
     */
    @TableField(value = "sys_user_nickname")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度要小于 255")
    /**
     * SYS用户nickname
    */
    private String sysUserNickname;

    /**
     * 手机号
     */
    @TableField(value = "sys_user_phone")
    @ApiModelProperty(value = "手机号")
    @Schema(description = "手机号")
    @Size(max = 255, message = "手机号最大长度要小于 255")
    /**
     * SYS用户phone
    */
    private String sysUserPhone;

    /**
     * 邮箱
     */
    @TableField(value = "sys_user_email")
    @ApiModelProperty(value = "邮箱")
    @Schema(description = "邮箱")
    @Size(max = 255, message = "邮箱最大长度要小于 255")
    /**
     * SYS用户email
    */
    private String sysUserEmail;

    /**
     * 邮箱是否已验证: 0:未验证 1:已验证
     */
    @Schema(description = "邮箱是否已验证: 0:未验证 1:已验证")
    /**
     * SYS用户邮箱已验证
    */
    private Integer sysUserEmailVerified;

    /**
     * 邮箱验证通过时间
     */
    @Schema(description = "邮箱验证通过时间")
    /**
     * SYS用户邮箱验证时间
    */
    private java.time.LocalDateTime sysUserEmailVerifyTime;

    /**
     * 身份证
     */
    @TableField(value = "sys_user_card")
    @ApiModelProperty(value = "身份证")
    @Schema(description = "身份证")
    @Size(max = 255, message = "身份证最大长度要小于 255")
    /**
     * SYS用户card
    */
    private String sysUserCard;

    /**
     * 头像地址
     */
    @Schema(description = "头像地址")
    /**
     * Avatar
    */
    private String avatar;

    /**
     * 性别（0:未知/男, 1:女, 2:保密）
     */
    @Schema(description = "性别")
    /**
     * SYS用户SEX
    */
    private Integer sysUserSex;

    /**
     * 头像框标识
     */
    @Schema(description = "头像框ID")
    /**
     * SYS用户avatarframe
    */
    private Integer sysUserAvatarFrame;

    /**
     * 是否显示头像框
     */
    @Schema(description = "是否显示头像框")
    /**
     * SYS用户avatarframeshow
    */
    private Integer sysUserAvatarFrameShow;

    /**
     * 生日
     */
    @Schema(description = "生日")
    /**
     * SYS用户birthday
    */
    private String sysUserBirthday;

    /**
     * 租户标识
     */
    /**
     * 租户标识
     */
    @Schema(description = "租户ID")
    /**
     * tenantid
    */
    private String tenantId;

    /**
     * 主部门标识
     */
    @Schema(description = "主部门ID")
    /**
     * sysdeptid
    */
    private Integer sysDeptId;

    /**
     * 负责部门标识集合
     */
    @Schema(description = "负责部门ID集合")
    /**
     * managedDept标识
    */
    private List<Integer> managedDeptIds;

    /**
     * 用户角色编码集合
     */
    @Schema(description = "用户角色编码集合")
    /**
     * 角色
    */
    private Set<String> roles;

    /**
     * 用户权限标识集合
     */
    @Schema(description = "用户权限标识集合")
    /**
     * Perms
    */
    private Set<String> perms;

    /**
     * 当前用户协议版本
     */
    @Schema(description = "当前用户协议版本")
    /**
     * Agreement版本
    */
    private String agreementVersion;

    /**
     * 当前用户协议更新时间
     */
    @Schema(description = "当前用户协议更新时间")
    /**
     * Agreement更新时间AT
    */
    private String agreementUpdatedAt;

    /**
     * 用户已确认的协议版本
     */
    @Schema(description = "用户已确认的协议版本")
    /**
     * Agreementaccepted版本
    */
    private String agreementAcceptedVersion;

    /**
     * 用户确认协议时间
     */
    @Schema(description = "用户确认协议时间")
    /**
     * agreementacceptedat
    */
    private String agreementAcceptedAt;

    /**
     * 是否需要确认最新协议
     */
    @Schema(description = "是否需要确认最新协议")
    /**
     * Agreementneedconfirm
    */
    private Boolean agreementNeedConfirm;

    /**
     * 是否强制修改密码: 0-否 1-是
     */
    @Schema(description = "是否强制修改密码: 0-否 1-是")
    /**
     * Mustchangepassword
    */
    private Integer mustChangePassword;
}
