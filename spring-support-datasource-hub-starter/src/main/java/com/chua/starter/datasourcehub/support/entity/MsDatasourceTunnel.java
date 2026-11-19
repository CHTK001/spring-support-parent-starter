package com.chua.starter.datasourcehub.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源 SSH 隧道配置（高级配置）。
 *
 * <p>目标库位于内网、无法从本机直连时，用隧道中转：REMOTE 为反向隧道（对应 ssh -R），
 * 把远端可达的端口映射回本机，再按本地端口连接。</p>
 *
 * <p>表 {@code datasource_tunnel}：业务列以完整表名作前缀；审计字段继承 {@link SysBase}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据源 SSH 隧道配置")
@TableName("datasource_tunnel")
public class MsDatasourceTunnel extends SysBase {

    /**
     * 串行版本 UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 数据源 ID
     */
    @TableField("datasource_tunnel_datasource_id")
    @Schema(description = "数据源 ID", example = "1")
    private Long datasourceId;

    /**
     * 隧道名称
     */
    @TableField("datasource_tunnel_name")
    @Schema(description = "隧道名称", example = "内网库反向隧道")
    private String tunnelName;

    /**
     * 隧道类型
     *
     * <p>LOCAL 本地转发（-L）；REMOTE 反向转发（-R）；DYNAMIC 动态 SOCKS5。</p>
     */
    @TableField("datasource_tunnel_type")
    @Schema(description = "隧道类型 LOCAL/REMOTE/DYNAMIC", example = "REMOTE")
    private String tunnelType;

    /**
     * SSH 服务器地址
     */
    @TableField("datasource_tunnel_ssh_host")
    @Schema(description = "SSH 服务器地址", example = "10.0.0.9")
    private String sshHost;

    /**
     * SSH 端口，空则 22
     */
    @TableField("datasource_tunnel_ssh_port")
    @Schema(description = "SSH 端口", example = "22")
    private Integer sshPort;

    /**
     * SSH 用户名
     */
    @TableField("datasource_tunnel_ssh_username")
    @Schema(description = "SSH 用户名", example = "tunnel")
    private String sshUsername;

    /**
     * 认证方式，PASSWORD 口令 / KEY 私钥
     */
    @TableField("datasource_tunnel_ssh_auth_type")
    @Schema(description = "认证方式 PASSWORD/KEY", example = "PASSWORD")
    private String sshAuthType;

    /**
     * SSH 口令（明文或 b64: 前缀，与台账口令两种存法共存）
     */
    @TableField("datasource_tunnel_ssh_password")
    @Schema(description = "SSH 口令", example = "******")
    private String sshPassword;

    /**
     * SSH 私钥内容
     */
    @TableField("datasource_tunnel_ssh_private_key")
    @Schema(description = "SSH 私钥内容", example = "-----BEGIN OPENSSH PRIVATE KEY-----")
    private String sshPrivateKey;

    /**
     * 私钥口令
     */
    @TableField("datasource_tunnel_ssh_passphrase")
    @Schema(description = "私钥口令", example = "******")
    private String sshPassphrase;

    /**
     * 本地端口，空则自动分配
     */
    @TableField("datasource_tunnel_local_port")
    @Schema(description = "本地端口", example = "0")
    private Integer localPort;

    /**
     * 远端目标主机
     */
    @TableField("datasource_tunnel_remote_host")
    @Schema(description = "远端目标主机", example = "127.0.0.1")
    private String remoteHost;

    /**
     * 远端目标端口
     */
    @TableField("datasource_tunnel_remote_port")
    @Schema(description = "远端目标端口", example = "3306")
    private Integer remotePort;

    /**
     * 本地绑定地址
     */
    @TableField("datasource_tunnel_bind_address")
    @Schema(description = "本地绑定地址", example = "127.0.0.1")
    private String bindAddress;

    /**
     * 状态，0 停用、1 启用
     */
    @TableField("datasource_tunnel_status")
    @Schema(description = "状态 0停用 1启用", example = "0")
    private Integer tunnelStatus;
}
