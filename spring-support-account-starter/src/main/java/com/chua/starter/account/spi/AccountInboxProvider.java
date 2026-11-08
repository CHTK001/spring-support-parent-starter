package com.chua.starter.account.spi;

import com.chua.starter.account.mail.MailboxFolder;
import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.account.mail.MailboxQuery;
import com.chua.starter.account.manage.store.SysAccount;

import java.util.List;
import java.util.Set;

/**
 * 收信 SPI —— 「打开邮箱 / 打开短信」背后真正的收信箱能力。
 *
 * <p>账号模块自己只做编排，不认识具体协议：邮件 IMAP/POP3 由
 * {@code email-imap} 实现提供，临时邮箱 / 接码平台等其它来源按同一契约扩展即可。
 * 实现通过 utilities 的 {@code @Spi} 注册，调用方用
 * {@code ServiceProvider.of(AccountInboxProvider.class).list()} 枚举。</p>
 *
 * <p><b>凭据边界</b>：明文凭据由编排层解密后作为 {@code secret} 传入，
 * 实现不得持久化、不得回显，也不得直接读账号表。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface AccountInboxProvider {

    /**
     * 收信实现标识（稳定，日志 / 界面展示用）。
     *
     * @return 实现标识，如 {@code email-imap}
     */
    String provider();

    /**
     * 该实现支持的账号种类。
     *
     * @return 账号种类集合，如 {@code EMAIL}
     */
    Set<String> accountKinds();

    /**
     * 判断该实现能否处理指定账号。
     *
     * <p>除种类外还要看必要配置是否齐备（主机、登录名），缺配置时返回 {@code false}，
     * 让界面给出「缺少主机」这类明确原因，而不是连接超时。</p>
     *
     * @param account 账号
     * @return 能处理返回 {@code true}
     */
    boolean supports(SysAccount account);

    /**
     * 列出可用文件夹（收件箱 / 已发送 / 草稿 …）。
     *
     * @param account 账号
     * @param secret  明文凭据
     * @return 文件夹清单（按展示顺序）
     * @throws Exception 连接或协议异常
     */
    List<MailboxFolder> folders(SysAccount account, String secret) throws Exception;

    /**
     * 取信列表（新的在前）。
     *
     * @param account 账号
     * @param secret  明文凭据
     * @param query   取信条件
     * @return 消息列表（{@code content} 为空，只带 {@code preview}）
     * @throws Exception 连接或协议异常
     */
    List<MailboxMessage> messages(SysAccount account, String secret, MailboxQuery query) throws Exception;

    /**
     * 取单封邮件详情（含正文）。
     *
     * @param account   账号
     * @param secret    明文凭据
     * @param folder    服务端文件夹名
     * @param messageId 消息标识
     * @return 消息详情；不存在返回 {@code null}
     * @throws Exception 连接或协议异常
     */
    MailboxMessage message(SysAccount account, String secret, String folder, String messageId) throws Exception;

    /**
     * 该实现能否回写已读标记。
     *
     * @return 支持返回 {@code true}（默认不支持）
     */
    default boolean supportsMarkSeen() {
        return false;
    }

    /**
     * 该实现能否删除邮件。
     *
     * @return 支持返回 {@code true}（默认不支持）
     */
    default boolean supportsDelete() {
        return false;
    }

    /**
     * 设置已读 / 未读标记。
     *
     * <p>默认实现直接抛 {@link UnsupportedOperationException}：只读来源（临时邮箱、
     * 接码平台）无法回写标记，界面据此禁用按钮，而不是假装成功。</p>
     *
     * @param account   账号
     * @param secret    明文凭据
     * @param folder    服务端文件夹名
     * @param messageId 消息标识
     * @param seen      {@code true} 标记为已读
     * @return 是否真正写入
     * @throws Exception 连接或协议异常
     */
    default boolean markSeen(SysAccount account, String secret, String folder, String messageId, boolean seen)
            throws Exception {
        throw new UnsupportedOperationException("该收信来源不支持修改已读标记");
    }

    /**
     * 删除单封邮件（移入已删除 / 打删除标记）。
     *
     * <p>默认实现直接抛 {@link UnsupportedOperationException}：只读来源无法删除。</p>
     *
     * @param account   账号
     * @param secret    明文凭据
     * @param folder    服务端文件夹名
     * @param messageId 消息标识
     * @return 是否真正删除
     * @throws Exception 连接或协议异常
     */
    default boolean delete(SysAccount account, String secret, String folder, String messageId) throws Exception {
        throw new UnsupportedOperationException("该收信来源不支持删除邮件");
    }
}
