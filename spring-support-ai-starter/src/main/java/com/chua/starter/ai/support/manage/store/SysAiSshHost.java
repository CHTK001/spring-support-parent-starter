package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 远程主机（SSH 主机簿）持久化实体。
 *
 * <p>对应前端 {@code /api/ssh-hosts} 契约（{@code IApiSshHost}），
 * 持久化到 {@code sys_ai_ssh_host} 表。</p>
 *
 * <p>密钥类字段（password/privateKey/passphrase/certificate/sudoPassword）
 * 写入后不回显明文，返回时统一脱敏为 {@code "***"}；连接测试与拨号时再解密使用。</p>
 *
 * <p>数据库列以表名 {@code sys_ai_ssh_host_} 作为前缀，实体字段保持简短语义名，
 * 通过 {@link TableField} 显式映射，前端契约稳定不变。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_ssh_host")
public class SysAiSshHost implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主机主键（uuidv7 字符串），由后端 {@code newId()} 生成并填充
     */
    @TableId(value = "sys_ai_ssh_host_id", type = IdType.INPUT)
    private String sshHostId; // ssh主机标识

    /**
     * 主机显示名称
     */
    @TableField("sys_ai_ssh_host_name")
    private String name; // 名称

    /**
     * 主机地址（IP 或域名）
     */
    @TableField("sys_ai_ssh_host_host")
    private String host; // 主机

    /**
     * SSH 端口号（默认 22）
     */
    @TableField("sys_ai_ssh_host_port")
    private Integer port; // 端口

    /**
     * 登录用户名
     */
    @TableField("sys_ai_ssh_host_username")
    private String username; // 用户名

    /**
     * 认证方式（密码 / Key / 证书 / Agent）
     */
    @TableField("sys_ai_ssh_host_auth_type")
    private String authType; // 认证类型

    /**
     * 登录密码（写入后不回显）
     */
    @TableField("sys_ai_ssh_host_password")
    private String password; // 密码

    /**
     * 私钥内容（写入后不回显）
     */
    @TableField("sys_ai_ssh_host_private_key")
    private String privateKey; // 私募Key

    /**
     * 私钥口令（passphrase，写入后不回显）
     */
    @TableField("sys_ai_ssh_host_passphrase")
    private String passphrase; // passphrase

    /**
     * 证书内容（写入后不回显）
     */
    @TableField("sys_ai_ssh_host_certificate")
    private String certificate; // 证书

    /**
     * sudo 密码（写入后不回显）
     */
    @TableField("sys_ai_ssh_host_sudo_password")
    private String sudoPassword; // sudo密码

    /**
     * 主机密钥指纹（首次连接后固定，连接过即永久绿）
     */
    @TableField("sys_ai_ssh_host_host_fingerprint")
    private String hostFingerprint; // 主机fingerprint

    /**
     * 主机状态（unknown / 错误 / ok，前端仅作提示列，不参与实时链路判断）
     */
    @TableField("sys_ai_ssh_host_status")
    private String status; // 状态

    /**
     * 最后连接成功时间（轮次 毫秒，未连接为 空）
     */
    @TableField("sys_ai_ssh_host_last_connected_at")
    private Long lastConnectedAt; // 最后一个连接at

    /**
     * 创建时间（轮次 毫秒）
     */
    @TableField("sys_ai_ssh_host_created_at")
    private Long createdAt; // 创建at

    /**
     * 最后更新时间（轮次 毫秒）
     */
    @TableField("sys_ai_ssh_host_updated_at")
    private Long updatedAt; // 更新at

    /**
     * 归属用户标识（登录用户名，凭据仅归属者可见）
     */
    @TableField("sys_ai_ssh_host_user_id")
    private String userId; // 归属用户
}
