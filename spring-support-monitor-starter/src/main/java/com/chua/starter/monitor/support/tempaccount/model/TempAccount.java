package com.chua.starter.monitor.support.tempaccount.model;

import com.chua.common.support.datasearch.email.model.EmailInfo;
import com.chua.common.support.datasearch.sms.model.SmsMessageInfo;
import com.chua.common.support.datasearch.sms.model.SmsPhoneInfo;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时账号 —— 一次性的随机虚拟身份。
 *
 * <p>随机生成一个虚拟号码（接码）与一个临时邮箱（收信），作为注册
 * 第三方服务的临时凭证。号码收到的短信与邮箱收到的邮件在内存中缓存，
 * 由 {@link com.chua.starter.monitor.support.tempaccount.TempAccountService}
 * 定时轮询增量抓取，并通过 SSE 实时推送到前端抽屉。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Builder
public class TempAccount {

    /**
     * 账号标识
     */
    private String id;

    /**
     * 随机虚拟号码（接码）
     */
    private SmsPhoneInfo phone;

    /**
     * 短信数据源名称（如 freephonenum / jiemahao）
     */
    private String smsProvider;

    /**
     * 随机临时邮箱（可为空 —— 部分环境邮件源不可用）
     */
    private String email;

    /**
     * 邮件数据源名称（如 mailtm / fce）
     */
    private String emailProvider;

    /**
     * 创建时间（毫秒）
     */
    private long createdAt;

    /**
     * 已收到的短信（倒序：最新在前）
     */
    @Builder.Default
    private final List<SmsMessageInfo> smsMessages = new ArrayList<>();

    /**
     * 已收到的邮件（倒序：最新在前）
     */
    @Builder.Default
    private final List<EmailInfo> emails = new ArrayList<>();

    /**
     * 已见短信指纹（内容签名 → 去重，避免重复广播）
     */
    @Builder.Default
    private final Set<String> seenSms = ConcurrentHashMap.newKeySet();

    /**
     * 已见邮件指纹（邮件 id → 去重）
     */
    @Builder.Default
    private final Set<String> seenEmail = ConcurrentHashMap.newKeySet();
}