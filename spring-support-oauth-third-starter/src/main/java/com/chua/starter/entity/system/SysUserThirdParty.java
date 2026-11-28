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

import java.io.Serializable;

/**
 * 用户第三方绑定表
 *
 * @since 2024/9/6
 * @author CH
 */
@Schema(description = "用户第三方绑定表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user_third_party")
public class SysUserThirdParty extends SysBase implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 主键标识
     */
    @TableId(value = "sys_user_third_party_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    /**
     * SYS用户thirdpartyid
    */
    private Integer sysUserThirdPartyId;

    /**
     * 第三方类型，如: gitee、wx_mini_app
     */
    @TableField(value = "sys_user_third_party_type")
    @Schema(description = "第三方类型，如: gitee、wx_mini_app")
    @Size(max = 255, message = "第三方类型最大长度需小于255")
    /**
     * SYS用户thirdparty类型
    */
    private String sysUserThirdPartyType;

    /**
     * 第三方信息名称
     */
    @TableField(value = "sys_user_third_party_name")
    @Schema(description = "第三方信息名称")
    @Size(max = 255, message = "第三方信息名称最大长度需小于255")
    /**
     * SYS用户thirdparty名称
    */
    private String sysUserThirdPartyName;

    /**
     * 第三方信息值
     */
    @TableField(value = "sys_user_third_party_value")
    @Schema(description = "第三方信息值")
    @Size(max = 255, message = "第三方信息值最大长度需小于255")
    /**
     * SYS用户thirdparty值
    */
    private String sysUserThirdPartyValue;

    /**
     * 当前系统用户标识
     */
    @TableField(value = "sys_user_id")
    @Schema(description = "当前系统用户ID")
    /**
     * SYS用户标识
    */
    private Integer sysUserId;

    /**
     * 第三方用户标识
     */
    @TableField(value = "sys_user_third_party_uid")
    @Schema(description = "第三方用户ID")
    @Size(max = 255, message = "第三方用户ID最大长度需小于255")
    /**
     * SYS用户thirdpartyuid
    */
    private String sysUserThirdPartyUid;

    /**
     * 第三方类型枚举
     * @author CH
     * @since 4.0.0
     */
    public enum Type {
        /**
         * Gitee
         */
        GITEE,
        /**
         * GitHub
         */
        GITHUB,
        /**
         * Google
         */
        GOOGLE,
        /**
         * 微信小程序
         */
        WX_MINI_APP,
        /**
         * 微信开放平台
         */
        WECHAT_OPEN,
        /**
         * 钉钉
         */
        DINGTALK,
        /**
         * 企业微信
         */
        WECHAT_ENTERPRISE,
        /**
         * OIDC 通用身份提供方
         */
        OIDC,
        /**
         * Wechat
         */
        WECHAT,
        /**
         * Wechat MP
         */
        WECHAT_MP,
        /**
         * QQ
         */
        QQ,
        /**
         * Alipay
         */
        ALIPAY,
        /**
         * Weibo
         */
        WEIBO,
        /**
         * Baidu
         */
        BAIDU,
        /**
         * Microsoft
         */
        MICROSOFT,
        /**
         * Taobao
         */
        TAOBAO,
        /**
         * Douyin
         */
        DOUYIN,
        /**
         * Feishu
         */
        FEISHU
    }
}
