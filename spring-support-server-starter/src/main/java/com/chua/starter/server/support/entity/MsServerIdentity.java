package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录身份实体
 * <p>
 * 对应表 {@code server_identity}，描述"用什么账号连主机"：
 * 密码身份直接存加密口令；公钥身份引用 {@link MsServerKey}。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "登录身份")
@TableName("server_identity")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsServerIdentity extends SysBase {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
     * 身份名称
     */
    @TableField("server_identity_name")
    @Schema(description = "身份名称", example = "root@prod")
    private String serverIdentityName; // 登录身份名称

    /**
     * 认证类型
     */
    @TableField("server_identity_auth_type")
    @Schema(description = "认证类型: PASSWORD / PUBLIC_KEY / MAILBOX", example = "PASSWORD")
    private String serverIdentityAuthType; // 登录身份认证类型

    /**
     * 登录用户名
     */
    @TableField("server_identity_username")
    @Schema(description = "登录用户名", example = "root")
    private String serverIdentityUsername; // 登录身份用户名

    /**
     * 登录密码（加密存储，仅 密码 类型）
     */
    @TableField("server_identity_password")
    @Schema(description = "登录密码(加密)")
    private String serverIdentityPassword; // 登录身份密码

    /**
     * 引用密钥 标识（仅 公共_键 类型）
     */
    @TableField("server_identity_key_id")
    @Schema(description = "引用密钥ID", example = "1")
    private Long serverIdentityKeyId; // 登录身份键标识

    /**
     * 邮箱来源（仅 MAILBOX 类型）：IMAP(真实邮箱) / TEMP(临时邮箱API)
     */
    @TableField("server_identity_email_source")
    @Schema(description = "邮箱来源: IMAP(真实邮箱)/TEMP(临时邮箱API)", example = "IMAP")
    private String serverIdentityEmailSource; // 登录身份email源

    /**
     * 临时邮箱服务商（仅 TEMP 来源）：fce/luckmail/mailnest/mailtm
     */
    @TableField("server_identity_provider")
    @Schema(description = "临时邮箱服务商(fce/luckmail/mailnest/mailtm)", example = "fce")
    private String serverIdentityProvider; // 登录身份提供者

    /**
     * 邮箱 IMAP 服务器（仅 MAILBOX 类型，空则按邮箱域名推断 imap.<域名>）
     */
    @TableField("server_identity_imap_host")
    @Schema(description = "邮箱IMAP服务器", example = "imap.qq.com")
    private String serverIdentityImapHost; // 登录身份imap主机

    /**
     * 邮箱 IMAP 端口（仅 MAILBOX 类型，默认 993）
     */
    @TableField("server_identity_imap_port")
    @Schema(description = "邮箱IMAP端口", example = "993")
    private Integer serverIdentityImapPort; // 登录身份imap端口

    /**
     * 备注
     */
    @TableField("server_identity_remark")
    @Schema(description = "备注", example = "管理员账号")
    private String serverIdentityRemark; // 登录身份评论
}
