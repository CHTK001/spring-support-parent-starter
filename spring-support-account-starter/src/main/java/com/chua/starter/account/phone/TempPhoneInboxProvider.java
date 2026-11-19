package com.chua.starter.account.phone;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.mail.MailboxFolder;
import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.account.mail.MailboxQuery;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.spi.AccountInboxProvider;

import java.util.List;
import java.util.Set;

/**
 * 临时手机号收信实现（本地目录轮询）。
 *
 * <p>只读来源：不支持已读回写与删除，{@link #supportsMarkSeen()} /
 * {@link #supportsDelete()} 保持默认 {@code false}，界面据此禁用按钮。</p>
 *
 * <p>短信来自 {@link com.chua.starter.account.schedule.SmsDirectoryPoller} 写入内存暂存库
 * {@link TempPhoneSmsStore} 的消息；不联网、不读账号表，{@code secret}
 * 传空即可。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Spi("temp-phone")
public class TempPhoneInboxProvider implements AccountInboxProvider {

    /**
     * 收信箱服务端名
     */
    private static final String INBOX = "INBOX";

    /**
     * {@inheritDoc}
     */
    @Override
    public String provider() {
        return TempPhoneService.PROVIDER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> accountKinds() {
        return Set.of(AccountKind.TEMP_PHONE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(SysAccount account) {
        return TempPhoneService.isTempPhone(account);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MailboxFolder> folders(SysAccount account, String secret) {
        int total = TempPhoneSmsStore.get().count(phone(account));
        MailboxFolder folder = new MailboxFolder();
        folder.setKey(INBOX);
        folder.setName("收信箱");
        folder.setRole("INBOX");
        folder.setTotal(total);
        folder.setUnread(0);
        return List.of(folder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MailboxMessage> messages(SysAccount account, String secret, MailboxQuery query) {
        return TempPhoneSmsStore.get().messages(phone(account), query);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MailboxMessage message(SysAccount account, String secret, String folder, String messageId) {
        return TempPhoneSmsStore.get().message(phone(account), messageId);
    }

    /**
     * 账号上的手机号。
     *
     * @param account 账号
     * @return 手机号
     */
    private String phone(SysAccount account) {
        String phone = account.getUsername();
        if (StringUtils.isBlank(phone)) {
            throw new IllegalStateException("临时手机号缺少号码");
        }
        return phone.trim();
    }
}
