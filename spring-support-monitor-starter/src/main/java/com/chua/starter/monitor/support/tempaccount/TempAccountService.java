package com.chua.starter.monitor.support.tempaccount;

import com.chua.common.support.datasearch.email.model.EmailInfo;
import com.chua.common.support.datasearch.email.spi.EmailProvider;
import com.chua.common.support.datasearch.sms.model.SmsMessageInfo;
import com.chua.common.support.datasearch.sms.model.SmsPhoneInfo;
import com.chua.common.support.datasearch.sms.spi.SmsCodeProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.support.tempaccount.model.TempAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时账号服务 —— 随机虚拟身份的创建、查询、删除与增量消息轮询。
 *
 * <p>一个临时账号 = 一个随机虚拟号码（接码 SPI）+ 一个随机临时邮箱（邮件 SPI）。
 * 创建时分别从 {@link SmsCodeProvider} 与 {@link EmailProvider} 随机抽取；
 * 随后 {@link #pollMessages()} 每 15 秒轮询一次所有存活账号的短信与邮件，
 * 检测到新增即通过 {@link TempAccountStreamRegistry} 广播增量事件，
 * 前端抽屉实时追加展示。</p>
 *
 * <p><b>数据源约定</b>：号码默认 {@code freephonenum}（无 Cloudflare 验证，
 * 当前网络直连可用）；邮箱默认 {@code mailtm}（免 API Key）。账号创建时若
 * 对应源暂不可用（如邮箱源未配置），对应通道置空，不影响另一通道。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TempAccountService {

    /**
     * 默认短信数据源
     */
    private static final String DEFAULT_SMS_PROVIDER = "freephonenum";

    /**
     * 默认邮件数据源（mail.tm，免 API Key）
     */
    private static final String DEFAULT_EMAIL_PROVIDER = "mail-tm";

    /**
     * 轮询间隔（毫秒）
     */
    private static final long POLL_INTERVAL_MS = 15_000L;

    /**
     * 已创建的临时账号（内存态）
     */
    private final Map<String, TempAccount> accounts = new ConcurrentHashMap<>();

    /**
     * SSE 广播注册表
     */
    private final TempAccountStreamRegistry streamRegistry;

    /**
     * 随机创建一个临时账号。
     *
     * <p>按 {@code type} 有选择地随机生成通道：{@code sms}（仅虚拟号码）、
     * {@code email}（仅临时邮箱）、{@code both}（号码 + 邮箱，默认）。
     * 指定通道生成失败（数据源不可用）时该通道置空，账号仍创建成功。</p>
     *
     * @param type 通道类型：sms / email / both
     * @return 新账号
     */
    public TempAccount create(String type) {
        boolean wantSms = type == null || type.isBlank() || "both".equalsIgnoreCase(type) || "sms".equalsIgnoreCase(type);
        boolean wantEmail = type == null || type.isBlank() || "both".equalsIgnoreCase(type) || "email".equalsIgnoreCase(type);

        TempAccount account = TempAccount.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .smsProvider(DEFAULT_SMS_PROVIDER)
                .emailProvider(DEFAULT_EMAIL_PROVIDER)
                .createdAt(System.currentTimeMillis())
                .build();

        // 1. 随机虚拟号码（sms / both）
        if (wantSms) {
            try {
                SmsCodeProvider sms = ServiceProvider.of(SmsCodeProvider.class)
                        .getExtension(DEFAULT_SMS_PROVIDER);
                if (sms != null) {
                    account.setPhone(sms.randomPhone());
                } else {
                    log.warn("[temp-account] 未找到短信数据源 {}", DEFAULT_SMS_PROVIDER);
                }
            } catch (Exception e) {
                log.warn("[temp-account] 随机号码失败: {}", e.getMessage());
            }
        }

        // 2. 随机临时邮箱（email / both）
        if (wantEmail) {
            try {
                EmailProvider emailProvider = ServiceProvider.of(EmailProvider.class)
                        .getExtension(DEFAULT_EMAIL_PROVIDER);
                if (emailProvider != null) {
                    String email = emailProvider.createEmail();
                    account.setEmail(email);
                } else {
                    log.warn("[temp-account] 未找到邮件数据源 {}", DEFAULT_EMAIL_PROVIDER);
                }
            } catch (Exception e) {
                log.warn("[temp-account] 随机邮箱失败: {}", e.getMessage());
            }
        }

        accounts.put(account.getId(), account);
        log.info("[temp-account] 创建临时账号 {} 号码={} 邮箱={}",
                account.getId(), account.getPhone() == null ? "-" : account.getPhone().getPhone(),
                account.getEmail() == null ? "-" : account.getEmail());

        // 建号立即广播，前端列表实时出现
        streamRegistry.broadcast(TempAccountStreamRegistry.EVENT_ACCOUNT, toView(account));
        // 首轮立即抓一次历史短信/邮件
        try {
            pollAccount(account, true);
        } catch (Exception e) {
            log.debug("[temp-account] 首轮抓取跳过: {}", e.getMessage());
        }
        return account;
    }

    /**
     * 查询全部临时账号（列表轻量视图，不含历史消息与内部去重集合）。
     *
     * @return 账号视图列表（按创建时间倒序）
     */
    public List<Map<String, Object>> list() {
        return accounts.values().stream()
                .sorted(Comparator.comparingLong(TempAccount::getCreatedAt).reversed())
                .map(this::toView)
                .toList();
    }

    /**
     * 查询单个临时账号详情（含全部短信与邮件）。
     *
     * @param id 账号标识
     * @return 账号详情；不存在返回 null
     */
    public TempAccount detail(String id) {
        return accounts.get(id);
    }

    /**
     * 删除临时账号。
     *
     * @param id 账号标识
     * @return true 删除成功
     */
    public boolean delete(String id) {
        TempAccount removed = accounts.remove(id);
        if (removed != null) {
            log.info("[temp-account] 删除临时账号 {}", id);
            streamRegistry.broadcast(TempAccountStreamRegistry.EVENT_REMOVE,
                    Map.of("id", id, "phone", removed.getPhone() == null ? "" : removed.getPhone().getPhone()));
            return true;
        }
        return false;
    }

    /**
     * 定时轮询：每 15 秒抓取所有账号的新短信 / 新邮件并广播增量。
     */
    @Scheduled(fixedRate = POLL_INTERVAL_MS, initialDelay = POLL_INTERVAL_MS)
    public void pollMessages() {
        if (accounts.isEmpty()) {
            return;
        }
        for (TempAccount account : accounts.values()) {
            try {
                pollAccount(account, false);
            } catch (Exception e) {
                log.debug("[temp-account] 轮询账号 {} 失败: {}", account.getId(), e.getMessage());
            }
        }
    }

    /**
     * 抓取单个账号的新消息（增量，去重后仅广播新增）。
     *
     * @param account 账号
     * @param initial 是否首轮（首轮把历史消息作为初始载荷广播，非增量）
     */
    private void pollAccount(TempAccount account, boolean initial) {
        List<SmsMessageInfo> newSms = new ArrayList<>();
        List<EmailInfo> newEmails = new ArrayList<>();

        // 1. 短信：抓取该号码的全部短信，与已见指纹对比取新增
        SmsPhoneInfo phone = account.getPhone();
        if (phone != null && phone.getSmsUrl() != null && !phone.getSmsUrl().isBlank()) {
            try {
                SmsCodeProvider sms = ServiceProvider.of(SmsCodeProvider.class)
                        .getExtension(account.getSmsProvider());
                if (sms != null) {
                    List<SmsMessageInfo> messages = sms.fetchMessages(phone);
                    if (messages != null) {
                        for (SmsMessageInfo m : messages) {
                            String fp = smsFingerprint(m);
                            if (account.getSeenSms().add(fp)) {
                                newSms.add(m);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("[temp-account] 账号 {} 短信抓取失败: {}", account.getId(), e.getMessage());
            }
        }

        // 2. 邮件：抓取邮箱最新邮件，与已见 id 对比取新增
        if (account.getEmail() != null && !account.getEmail().isBlank()) {
            try {
                EmailProvider emailProvider = ServiceProvider.of(EmailProvider.class)
                        .getExtension(account.getEmailProvider());
                if (emailProvider != null) {
                    List<EmailInfo> emails = emailProvider.fetchEmails(account.getEmail());
                    if (emails != null) {
                        for (EmailInfo e : emails) {
                            String fp = emailFingerprint(e);
                            if (account.getSeenEmail().add(fp)) {
                                newEmails.add(e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("[temp-account] 账号 {} 邮件抓取失败: {}", account.getId(), e.getMessage());
            }
        }

        if (newSms.isEmpty() && newEmails.isEmpty()) {
            return;
        }

        // 合并进缓存（倒序：最新在前）
        synchronized (account) {
            newSms.forEach(m -> account.getSmsMessages().add(0, m));
            newEmails.forEach(e -> account.getEmails().add(0, e));
        }

        Map<String, Object> payload = Map.of(
                "id", account.getId(),
                "sms", newSms,
                "emails", newEmails);
        int delivered = streamRegistry.broadcast(TempAccountStreamRegistry.EVENT_MESSAGE, payload);
        log.info("[temp-account] 账号 {} 新短信 {} 条 / 新邮件 {} 封，送达 {} 连接",
                account.getId(), newSms.size(), newEmails.size(), delivered);
    }

    /**
     * 计算短信指纹（内容哈希），用于去重。
     *
     * @param m 短信
     * @return 指纹
     */
    private static String smsFingerprint(SmsMessageInfo m) {
        return (m.getFrom() == null ? "" : m.getFrom()) + "|" + (m.getContent() == null ? "" : m.getContent());
    }

    /**
     * 计算邮件指纹（id 优先，缺失则主题 + 发件人），用于去重。
     *
     * @param e 邮件
     * @return 指纹
     */
    private static String emailFingerprint(EmailInfo e) {
        if (e.getId() != null && !e.getId().isBlank()) {
            return "id:" + e.getId();
        }
        return "sig:" + (e.getSubject() == null ? "" : e.getSubject()) + "|"
                + (e.getFrom() == null ? "" : e.getFrom());
    }

    /**
     * 转列表视图（供前端渲染）。
     *
     * @param account 账号
     * @return 视图载荷
     */
    private Map<String, Object> toView(TempAccount account) {
        Map<String, Object> phone = null;
        if (account.getPhone() != null) {
            SmsPhoneInfo p = account.getPhone();
            phone = Map.of(
                    "phone", p.getPhone() == null ? "" : p.getPhone(),
                    "countryCode", p.getCountryCode() == null ? "" : p.getCountryCode(),
                    "countryName", p.getCountryName() == null ? "" : p.getCountryName(),
                    "smsUrl", p.getSmsUrl() == null ? "" : p.getSmsUrl());
        }
        return Map.of(
                "id", account.getId(),
                "smsProvider", account.getSmsProvider() == null ? "" : account.getSmsProvider(),
                "emailProvider", account.getEmailProvider() == null ? "" : account.getEmailProvider(),
                "email", account.getEmail() == null ? "" : account.getEmail(),
                "createdAt", account.getCreatedAt(),
                "smsCount", account.getSmsMessages().size(),
                "emailCount", account.getEmails().size(),
                "phone", phone);
    }
}