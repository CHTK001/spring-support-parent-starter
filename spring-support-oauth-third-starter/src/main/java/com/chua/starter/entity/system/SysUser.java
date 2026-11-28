package com.chua.starter.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 用户表
 *
 * @since 2024/8/7
 * @author CH
 */
@Schema(description = "用户表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user")
public class SysUser extends SysBase implements Serializable {
    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 用户标识
     */
    @TableId(value = "sys_user_id", type = IdType.AUTO)
    @Schema(description = "用户ID")
    @NotNull(message = "用户ID不能为空")
    /**
     * SYS用户标识
    */
    private Integer sysUserId;

    /**
     * 账号
     */
    @TableField(value = "sys_user_username")
    @Schema(description = "账号")
    @Size(max = 255, message = "账号最大长度需小于255")
    @NotEmpty(message = "账号不能为空", groups = {AddGroup.class})
    /**
     * SYS用户用户名
    */
    private String sysUserUsername;

    /**
     * 名称
     */
    @TableField(value = "sys_user_nickname")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度需小于255")
    /**
     * SYS用户nickname
    */
    private String sysUserNickname;

    /**
     * 居住地址
     */
    @TableField(value = "sys_user_full_address")
    @Schema(description = "居住地址")
    @Size(max = 400, message = "居住地址最大长度需小于400")
    /**
     * SYS用户完整地址
    */
    private String sysUserFullAddress;

    /**
     * 密码
     */
    @TableField(value = "sys_user_password")
    @Schema(description = "密码")
    @Size(max = 255, message = "密码最大长度需小于255")
    @NotEmpty(message = "密码不能为空", groups = {AddGroup.class})
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    /**
     * SYS用户密码
    */
    private String sysUserPassword;

    /**
     * 头像
     */
    @TableField(value = "sys_user_avatar")
    @Schema(description = "头像")
    @Size(max = 4000, message = "头像最大长度需小于4000")
    /**
     * SYS用户avatar
    */
    private String sysUserAvatar;

    /**
     * 盐
     */
    @TableField(value = "sys_user_salt")
    @Schema(description = "盐")
    @Size(max = 255, message = "盐最大长度需小于255")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    /**
     * SYS用户salt
    */
    private String sysUserSalt;

    /**
     * TOTP
     */
    @TableField(value = "sys_user_totp_code")
    @Schema(description = "TOTP")
    /**
     * SYS用户totp代码
    */
    private String sysUserTotpCode;

    /**
     * 是否删除; 0: 未删除; 非0: 已删除(取值为该行主键，保证墓碑行之间不互相冲突)
     */
    @TableField(value = "sys_user_delete")
    @Schema(description = "是否删除; 0: 未删除; 非0: 已删除")
    @TableLogic(value = "0", delval = "sys_user_id")
    /**
     * SYS用户删除
    */
    private Integer sysUserDelete;

    /**
     * 部门标识
     */
    @TableField(value = "sys_dept_id")
    @Schema(description = "部门ID")
    /**
     * sysdeptid
    */
    private Integer sysDeptId;

    /**
     * 是否启用; 0:禁用
     */
    @TableField(value = "sys_user_status")
    @Schema(description = "是否启用; 0:禁用")
    /**
     * SYS用户状态
    */
    private Integer sysUserStatus;

    /**
     * 手机号
     */
    @TableField(value = "sys_user_phone")
    @Schema(description = "手机号")
    @Size(max = 255, message = "手机号最大长度需小于255")
    /**
     * SYS用户phone
    */
    private String sysUserPhone;

    /**
     * 邮箱
     */
    @TableField(value = "sys_user_email")
    @Schema(description = "邮箱")
    @Size(max = 255, message = "邮箱最大长度需小于255")
    /**
     * SYS用户email
    */
    private String sysUserEmail;

    /**
     * 性别; 0:男 1:女 2:其他
     */
    @TableField(value = "sys_user_sex")
    @Schema(description = "性别; 0:男 1:女 2:其他")
    /**
     * SYS用户SEX
    */
    private Integer sysUserSex;

    /**
     * 登录次数
     */
    @TableField(value = "sys_user_login_count")
    @Schema(description = "登录次数")
    /**
     * SYS用户long数量
    */
    private Integer sysUserLongCount;

    /**
     * 是否系统账号; 0: 非系统账号
     */
    @TableField(value = "sys_user_in_system")
    @Schema(description = "是否系统账号; 0: 非系统账号")
    /**
     * SYS用户insystem
    */
    private Integer sysUserInSystem;

    /**
     * 身份证
     */
    @TableField(value = "sys_user_card")
    @Schema(description = "身份证")
    @Size(max = 255, message = "身份证最大长度需小于255")
    /**
     * SYS用户卡片
    */
    private String sysUserCard;

    /**
     * 备注
     */
    @TableField(value = "sys_user_remark")
    @Schema(description = "备注")
    @Size(max = 255, message = "备注最大长度需小于255")
    /**
     * SYS用户评论
    */
    private String sysUserRemark;

    /**
     * 最后一次登录地点
     */
    @TableField(value = "sys_user_last_address")
    @Schema(description = "最后一次登录地点")
    @Size(max = 255, message = "最后一次登录地点最大长度需小于255")
    /**
     * SYS用户最后地址
    */
    private String sysUserLastAddress;

    /**
     * 注册地点
     */
    @TableField(value = "sys_user_register_address")
    @Schema(description = "注册地点")
    /**
     * SYS用户注册地址
    */
    private String sysUserRegisterAddress;

    /**
     * 最后一次登录IP
     */
    @TableField(value = "sys_user_last_ip")
    @Schema(description = "最后一次登录IP")
    @Size(max = 255, message = "最后一次登录IP最大长度需小于255")
    /**
     * SYS用户最后IP
    */
    private String sysUserLastIp;

    /**
     * 注册IP
     */
    @TableField(value = "sys_user_register_ip")
    @Schema(description = "注册IP")
    @Size(max = 255, message = "注册IP最大长度需小于255")
    /**
     * SYS用户注册ip
    */
    private String sysUserRegisterIp;

    /**
     * 客户端标识
     */
    @TableField(value = "sys_client_id")
    @Schema(description = "客户端ID")
    /**
     * SYS客户端标识
    */
    private String sysClientId;

    /**
     * 生日
     */
    @TableField(value = "sys_user_birthday")
    @Schema(description = "生日")
    /**
     * SYS用户birthday
    */
    private String sysUserBirthday;

    /**
     * 头像框标识
     */
    @TableField(value = "sys_user_avatar_frame")
    @Schema(description = "头像框ID")
    /**
     * SYS用户avatarframe
    */
    private Integer sysUserAvatarFrame;

    /**
     * 最后登录时间
     */
    @TableField(value = "sys_user_last_login_time")
    @Schema(description = "最后登录时间")
    /**
     * SYS用户最后登录时间
    */
    private java.time.LocalDateTime sysUserLastLoginTime;

    /**
     * 最后登录IP
     */
    @TableField(value = "sys_user_last_ip")
    @Schema(description = "最后登录IP")
    /**
     * SYS用户最后登录IP
    */
    private String sysUserLastLoginIp;

    /**
     * 密码修改时间
     */
    @TableField(value = "sys_user_password_update_time")
    @Schema(description = "密码修改时间")
    /**
     * SYS用户密码更新时间
    */
    private java.time.LocalDateTime sysUserPasswordUpdateTime;

    /**
     * 是否强制修改密码: 0-否 1-是
     */
    @TableField(value = "sys_user_must_change_password")
    @Schema(description = "是否强制修改密码: 0-否 1-是")
    /**
     * SYS用户必须更改密码
    */
    private Integer sysUserMustChangePassword = 0;

    /**
     * 是否显示头像框; 0:不显示 1:显示
     */
    @TableField(value = "sys_user_avatar_frame_show")
    @Schema(description = "是否显示头像框; 0:不显示 1:显示")
    /**
     * SYS用户avatarframeshow
    */
    private Integer sysUserAvatarFrameShow;

    /**
     * 邮箱是否已验证; 0:未验证 1:已验证
     */
    @TableField(value = "sys_user_email_verified")
    @Schema(description = "邮箱是否已验证; 0:未验证 1:已验证")
    /**
     * SYS用户邮箱已验证
    */
    private Integer sysUserEmailVerified = 0;

    /**
     * 邮箱验证通过时间
     */
    @TableField(value = "sys_user_email_verify_time")
    @Schema(description = "邮箱验证通过时间")
    /**
     * SYS用户邮箱验证时间
    */
    private java.time.LocalDateTime sysUserEmailVerifyTime;

    /**
     * 角色列表(系统生成)
     */
    @Schema(description = "角色列表(系统生成)")
    @TableField(exist = false)
    /**
     * 用户角色
    */
    private List<SysRole> userRoles;

    /**
     * 岗位列表(系统生成)
     */
    @Schema(description = "岗位列表(系统生成)")
    @TableField(exist = false)
    /**
     * 用户岗位
    */
    private List<SysPost> userPosts;

    /**
     * 岗位标识列表(保存/更新时传入)
     */
    @Schema(description = "岗位ID列表(保存/更新时传入)")
    @TableField(exist = false)
    /**
     * 岗位标识
    */
    private List<Integer> postIds;

    /**
     * 租户标识
     */
    @TableField(exist = false)
    /**
     * systenantid
    */
    private Integer sysTenantId;
}
