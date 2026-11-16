package com.chua.starter.account.mail;

import lombok.Data;

import java.io.Serializable;

/**
 * 邮箱账号的连通性探测目标。
 *
 * <p>把「用哪个主机 / 端口 / 账号 / 凭据 / 加密方式握手」这件事从账号配置里解析出来，
 * 交给调用方做一次真实连接。主机 / 端口 / 加密的推导规则只写在
 * {@link MailboxService#mailProbeTarget()} 一处，收信、发信与探测因此不会各写一份口径。</p>
 *
 * <p>本对象含<b>明文凭据</b>，只在进程内传递，不得直接序列化给前端。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Data
public class MailProbeTarget implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账号标识
     */
    private String accountId;

    /**
     * 账号名称（探测结果里展示用）
     */
    private String accountName;

    /**
     * SMTP 主机
     */
    private String host;

    /**
     * SMTP 端口
     */
    private int port;

    /**
     * 登录名（邮箱地址）
     */
    private String username;

    /**
     * 明文授权码
     */
    private String password;

    /**
     * 是否启用隐式 TLS（SMTPS / IMAPS）
     */
    private boolean ssl;

    /**
     * 是否启用 STARTTLS（仅明文端口升级时用，与 {@link #ssl} 互斥）
     */
    private boolean starttls;

    /**
     * IMAP 主机
     */
    private String imapHost;

    /**
     * IMAP 端口
     */
    private int imapPort;
}
