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
 * 对应表 {@code server_key}，私钥与口令加密存储，接口层永不回显明文。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "SSH密钥")
@TableName("server_key")
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
    @TableField("server_key_name")
    @Schema(description = "密钥名称", example = "ubuntu-prod-key")
    private String serverKeyName; // SSH密钥名称

    /**
     * 密钥类型
     */
    @TableField("server_key_type")
    @Schema(description = "密钥类型: RSA / ED25519 / ECDSA", example = "RSA")
    private String serverKeyType; // SSH密钥类型

    /**
     * 公钥内容（明文存储，可对外展示）
     */
    @TableField("server_key_public_key")
    @Schema(description = "公钥内容")
    private String serverKeyPublicKey; // SSH密钥公共键

    /**
     * 私钥内容（加密存储）
     */
    @TableField("server_key_private_key")
    @Schema(description = "私钥内容(加密)")
    private String serverKeyPrivateKey; // SSH密钥私募键

    /**
     * 私钥口令（加密存储）
     */
    @TableField("server_key_password")
    @Schema(description = "私钥口令(加密)")
    private String serverKeyPassword; // SSH密钥密码

    /**
     * 备注
     */
    @TableField("server_key_remark")
    @Schema(description = "备注", example = "生产环境统一登录密钥")
    private String serverKeyRemark; // SSH密钥评论
}
