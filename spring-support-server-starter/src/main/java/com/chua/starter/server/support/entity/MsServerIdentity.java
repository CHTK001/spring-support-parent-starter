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
 * 对应表 {@code ms_server_identity}，描述"用什么账号连主机"：
 * 密码身份直接存加密口令；公钥身份引用 {@link MsServerKey}。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "登录身份")
@TableName("ms_server_identity")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsServerIdentity extends SysBase {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 身份名称
     */
    @TableField("ms_server_identity_name")
    @Schema(description = "身份名称", example = "root@prod")
    private String msServerIdentityName;

    /**
     * 认证类型
     */
    @TableField("ms_server_identity_auth_type")
    @Schema(description = "认证类型: PASSWORD / PUBLIC_KEY / MAILBOX", example = "PASSWORD")
    private String msServerIdentityAuthType;

    /**
     * 登录用户名
     */
    @TableField("ms_server_identity_username")
    @Schema(description = "登录用户名", example = "root")
    private String msServerIdentityUsername;

    /**
     * 登录密码（加密存储，仅 PASSWORD 类型）
     */
    @TableField("ms_server_identity_password")
    @Schema(description = "登录密码(加密)")
    private String msServerIdentityPassword;

    /**
     * 引用密钥 ID（仅 PUBLIC_KEY 类型）
     */
    @TableField("ms_server_identity_key_id")
    @Schema(description = "引用密钥ID", example = "1")
    private Long msServerIdentityKeyId;

    /**
     * 邮箱来源（仅 MAILBOX 类型）：IMAP(真实邮箱) / TEMP(临时邮箱API)
     */
    @TableField("ms_server_identity_email_source")
    @Schema(description = "邮箱来源: IMAP(真实邮箱)/TEMP(临时邮箱API)", example = "IMAP")
    private String msServerIdentityEmailSource;

    /**
     * 临时邮箱服务商（仅 TEMP 来源）：fce/luckmail/mailnest/mailtm
     */
    @TableField("ms_server_identity_provider")
    @Schema(description = "临时邮箱服务商(fce/luckmail/mailnest/mailtm)", example = "fce")
    private String msServerIdentityProvider;

    /**
     * 邮箱 IMAP 服务器（仅 MAILBOX 类型，空则按邮箱域名推断 imap.<域名>）
     */
    @TableField("ms_server_identity_imap_host")
    @Schema(description = "邮箱IMAP服务器", example = "imap.qq.com")
    private String msServerIdentityImapHost;

    /**
     * 邮箱 IMAP 端口（仅 MAILBOX 类型，默认 993）
     */
    @TableField("ms_server_identity_imap_port")
    @Schema(description = "邮箱IMAP端口", example = "993")
    private Integer msServerIdentityImapPort;

    /**
     * 备注
     */
    @TableField("ms_server_identity_remark")
    @Schema(description = "备注", example = "管理员账号")
    private String msServerIdentityRemark;
}
