package com.chua.starter.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 微信用户表
 *
 * 主要存储通过微信体系接入的用户基础信息
 *
 * @since 2026/08/03
 * @author CH
 */
@Schema(description = "微信用户表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_wechat_user")
public class SysWechatUser extends SysBase implements Serializable {

    @Serial
    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 主键标识
     */
    @TableId(value = "sys_wechat_user_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    /**
     * syswechat用户标识
    */
    private Integer sysWechatUserId;

    /**
     * 微信 openid
     */
    @TableField(value = "sys_wechat_open_id")
    @Schema(description = "微信 openid")
    @Size(max = 255, message = "微信 openid 最大长度需小于255")
    /**
     * syswechatopenid
    */
    private String sysWechatOpenId;

    /**
     * 微信 unionid
     */
    @TableField(value = "sys_wechat_union_id")
    @Schema(description = "微信 unionid")
    @Size(max = 255, message = "微信 unionid 最大长度需小于255")
    /**
     * syswechatunionid
    */
    private String sysWechatUnionId;

    /**
     * 昵称
     */
    @TableField(value = "sys_wechat_nickname")
    @Schema(description = "昵称")
    @Size(max = 255, message = "昵称最大长度需小于255")
    /**
     * syswechatnickname
    */
    private String sysWechatNickname;

    /**
     * 头像
     */
    @TableField(value = "sys_wechat_avatar")
    @Schema(description = "头像")
    @Size(max = 4000, message = "头像最大长度需小于4000")
    /**
     * syswechatavatar
    */
    private String sysWechatAvatar;

    /**
     * 绑定的系统用户标识
     */
    @TableField(value = "sys_user_id")
    @Schema(description = "绑定的系统用户ID")
    /**
     * SYS用户标识
    */
    private Integer sysUserId;
}

