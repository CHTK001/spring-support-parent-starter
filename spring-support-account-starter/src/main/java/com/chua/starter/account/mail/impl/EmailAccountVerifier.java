package com.chua.starter.account.mail.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.spi.AccountVerifier;
import jakarta.mail.Store;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮箱账号凭据校验（IMAP / IMAPS 登录探活）。
 *
 * <p><b>为什么是 IMAP 而不是 SMTP</b>：多数服务商的 SMTP 口令与网页登录口令分离，
 * SMTP 能发信不代表「这个邮箱还能收信」；而账号页要回答的正是「能不能打开收件箱」，
 * 因此校验直接复用 {@link EmailInboxProvider} 的 IMAP 主机 / 端口 / 加密推导做一次
 * 真实登录。收信与校验共用 {@code openStore}，两处口径不会漂移。</p>
 *
 * <p>校验结果只做三件事：登录、拿默认文件夹列表、断开。不读任何邮件，
 * 因此不会在服务端留下「已读」副作用。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Spi("email-imap-verify")
public class EmailAccountVerifier implements AccountVerifier {

    /**
     * 支持的账号种类
     */
    private static final String KIND_EMAIL = AccountKind.EMAIL;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(String kind) {
        return KIND_EMAIL.equalsIgnoreCase(kind);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> verify(SysAccount account, String plainSecret) {
        Map<String, Object> result = new LinkedHashMap<>(4);
        if (account == null) {
            result.put("valid", false);
            result.put("message", "账号不存在");
            return result;
        }
        if (StringUtils.isBlank(account.getUsername())) {
            result.put("valid", false);
            result.put("message", "缺少邮箱地址（登录名），无法登录收信服务");
            return result;
        }
        String host = EmailInboxProvider.imapHost(account);
        if (StringUtils.isBlank(host)) {
            result.put("valid", false);
            result.put("message", "缺少服务地址（SMTP / IMAP 主机），无法定位收信服务");
            return result;
        }
        Store store = null;
        try {
            store = EmailInboxProvider.openStore(account, plainSecret);
            int folders = 0;
            try {
                var root = store.getDefaultFolder();
                folders = root == null || root.list("*") == null ? 1 : root.list("*").length;
            } catch (Exception ignored) {
                // 登录已成功，列文件夹失败不影响「凭据有效」这个结论
                folders = 1;
            }
            result.put("valid", true);
            result.put("message", "登录成功：" + host + ":" + EmailInboxProvider.imapPort(account)
                    + "（" + (EmailInboxProvider.ssl(account) ? "IMAPS" : "IMAP") + "，"
                    + Math.max(folders, 1) + " 个文件夹）");
            return result;
        } catch (Exception e) {
            result.put("valid", false);
            result.put("message", "登录失败：" + rootMessage(e));
            return result;
        } finally {
            close(store);
        }
    }

    /**
     * 关闭连接（忽略异常）。
     *
     * @param store 连接
     */
    private void close(Store store) {
        if (store == null || !store.isConnected()) {
            return;
        }
        try {
            store.close();
        } catch (Exception ignored) {
            // 关闭失败不影响校验结论
        }
    }

    /**
     * 取最内层异常信息。
     *
     * @param error 异常
     * @return 可读信息
     */
    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return StringUtils.isBlank(message) ? cause.getClass().getSimpleName() : message;
    }
}
