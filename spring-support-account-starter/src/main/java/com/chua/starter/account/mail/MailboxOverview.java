package com.chua.starter.account.mail;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 信箱概览：账号能否收信 / 发信，以及可用的文件夹。
 *
 * <p>界面用它决定右侧按钮是否可点、空态提示什么原因，因此
 * {@code message} 必须写清「为什么不可用」（没有收信实现 / 缺少主机 / 缺少凭据）。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Data
public class MailboxOverview implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账号标识
     */
    private String accountId;

    /**
     * 账号种类（EMAIL / SMS）
     */
    private String kind;

    /**
     * 账号展示名
     */
    private String accountName;

    /**
     * 账号地址（邮箱地址 / 手机号）
     */
    private String address;

    /**
     * 是否具备收信能力
     */
    private Boolean supported;

    /**
     * 是否具备发信能力
     */
    private Boolean sendable;

    /**
     * 收信实现标识（如 {@code email-imap}）
     */
    private String provider;

    /**
     * 不可用原因（可用时为空）
     */
    private String message;

    /**
     * 可用文件夹
     */
    private List<MailboxFolder> folders;
}
