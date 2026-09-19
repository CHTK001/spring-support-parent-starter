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
 * SSH 密钥库实体
 * <p>
 * 对应表 {@code ms_server_key}，私钥与口令加密存储，接口层永不回显明文。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "SSH密钥")
@TableName("ms_server_key")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsServerKey extends SysBase {

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
     * 密钥名称
     */
    @TableField("ms_server_key_name")
    @Schema(description = "密钥名称", example = "ubuntu-prod-key")
    private String msServerKeyName; // ms服务端键名称

    /**
     * 密钥类型
     */
    @TableField("ms_server_key_type")
    @Schema(description = "密钥类型: RSA / ED25519 / ECDSA", example = "RSA")
    private String msServerKeyType; // ms服务端键类型

    /**
     * 公钥内容（明文存储，可对外展示）
     */
    @TableField("ms_server_key_public_key")
    @Schema(description = "公钥内容")
    private String msServerKeyPublicKey; // ms服务端键公共键

    /**
     * 私钥内容（加密存储）
     */
    @TableField("ms_server_key_private_key")
    @Schema(description = "私钥内容(加密)")
    private String msServerKeyPrivateKey; // ms服务端键私募键

    /**
     * 私钥口令（加密存储）
     */
    @TableField("ms_server_key_password")
    @Schema(description = "私钥口令(加密)")
    private String msServerKeyPassword; // ms服务端键密码

    /**
     * 备注
     */
    @TableField("ms_server_key_remark")
    @Schema(description = "备注", example = "生产环境统一登录密钥")
    private String msServerKeyRemark; // ms服务端键评论
}
