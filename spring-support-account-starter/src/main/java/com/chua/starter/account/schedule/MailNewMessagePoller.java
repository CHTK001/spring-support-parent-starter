package com.chua.starter.account.schedule;

import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.account.mail.MailboxQuery;
import com.chua.starter.account.mail.MailboxService;
import com.chua.starter.account.mail.notify.SocketNewMailPublisher;
import com.chua.starter.account.manage.event.AccountEventSseService;
import com.chua.starter.account.manage.service.ManageAccountService;
import com.chua.starter.account.manage.store.SysAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 新邮件轮询器。
 *
 * <p>按固定间隔轮询开启了 {@code config.notifyNew} 的邮件账号，取
 * {@code since=上次轮询时间} 之后的新邮件；发现新邮件时通过
 * {@link SocketNewMailPublisher} 推送 Socket 事件。</p>
 *
 * <p><b>副作用控制（R5）</b>：整个类被 {@code @ConditionalOnClass(SocketSessionTemplate.class)}
 * 兜底，宿主未引入 spring-support-socket-starter 时 Bean 不创建；
 * 再由 {@code @ConditionalOnProperty} 按 {@code plugin.account.mail-notify.enable}
 * 控制（默认 {@code false}）：开关关闭时 Bean 不注册，连
 * {@code @EnableScheduling} 的定时任务都不会带进宿主，只有显式开启后才开始轮询。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnClass(com.chua.socket.support.session.SocketSessionTemplate.class)
@ConditionalOnProperty(prefix = "plugin.account.mail-notify", name = "enable", havingValue = "true")
public class MailNewMessagePoller {

    /**
     * 每次最多取多少封新邮件（只需知道是否有新邮件，不必拉全量）
     */
    private static final int POLL_LIMIT = 10;

    private final ManageAccountService accountService;
    private final MailboxService mailboxService;
    private final SocketNewMailPublisher publisher;

    /**
     * 账号页统一 SSE 事件服务（新邮件同时走 SSE，前端逐步切换到单连接）
     */
    private final AccountEventSseService eventSseService;

    /**
     * 各账号上次轮询时间戳（毫秒），仅内存保留，重启后重新建基线。
     * {@code null} 表示尚未轮询过，首次只记基线不推送历史邮件。
     */
    private final Map<String, Long> lastPollAt = new ConcurrentHashMap<>();

    /**
     * 开关兜底（Bean 层已由 {@code @ConditionalOnProperty} 控制，此处再防御一次）
     */
    @Value("${plugin.account.mail-notify.enable:false}")
    private boolean enable;

    /**
     * 构造新邮件轮询器。
     *
     * @param accountService  账号服务
     * @param mailboxService  邮箱服务
     * @param publisher       Socket 新邮件发布器
     * @param eventSseService 账号页统一 SSE 事件服务
     */
    public MailNewMessagePoller(ManageAccountService accountService,
                                MailboxService mailboxService,
                                SocketNewMailPublisher publisher,
                                AccountEventSseService eventSseService) {
        this.accountService = accountService;
        this.mailboxService = mailboxService;
        this.publisher = publisher;
        this.eventSseService = eventSseService;
    }

    /**
     * 固定延迟轮询。开关由 {@code @ConditionalOnProperty} 控制在 Bean 层生效，
     * 关闭时连定时任务都不会注册；此处再兜底一次，避免误配置时做无谓收信。
     */
    @Scheduled(fixedDelayString = "${plugin.account.mail-notify.interval.ms:60000}")
    public void poll() {
        if (!enable) {
            return;
        }
        List<SysAccount> accounts;
        try {
            // 系统级轮询没有登录态，list() 的归属过滤会把个人邮箱账号漏掉，必须走 listInternal
            accounts = accountService.listInternal("EMAIL");
        } catch (Exception e) {
            log.warn("[mail-notify] 读取邮件账号失败: {}", e.getMessage());
            return;
        }
        if (CollectionUtils.isEmpty(accounts)) {
            return;
        }
        for (SysAccount account : accounts) {
            pollOne(account);
        }
    }

    /**
     * 轮询单个邮件账号：读取新邮件并按需推送。
     *
     * <p>synchronized：宿主侧可能存在多个调度注册（不同 {@code @EnableScheduling} 各带一个
     * 后置处理器），串行化同一账号的轮询，避免两个调度线程同时看到同一批新邮件而重复推送。</p>
     *
     * @param account 邮件账号
     */
    private synchronized void pollOne(SysAccount account) {
        Map<String, Object> config = account.getConfig();
        if (config == null || !Boolean.TRUE.equals(config.get("notifyNew"))) {
            return;
        }
        Object modeObj = config.get("inboxMode");
        if (modeObj == null) {
            return;
        }
        String accountId = account.getAccountId();
        Long since = lastPollAt.get(accountId);
        if (since == null) {
            // 首次轮询：只记基线，不推送历史邮件，避免启动瞬间刷屏
            lastPollAt.put(accountId, System.currentTimeMillis());
            return;
        }
        MailboxQuery query = new MailboxQuery();
        query.setFolder("INBOX");
        query.setLimit(POLL_LIMIT);
        query.setSince(since);
        List<MailboxMessage> messages;
        try {
            // 系统级读取：messages() 的归属断言在无登录态的轮询线程会拒绝个人账号
            messages = mailboxService.messagesInternal(accountId, query);
        } catch (Exception e) {
            log.warn("[mail-notify] 轮询账号失败: accountId={}, error={}", accountId, e.getMessage());
            return;
        }
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }
        // 更新基线为当前时间：只推送本次发现的新邮件
        lastPollAt.put(accountId, System.currentTimeMillis());
        MailboxMessage latest = messages.get(0); // 列表最新在前
        publisher.publish(accountId, account.getName(), messages.size(), latest);
        publishSse(account, messages.size(), latest);
        log.info("[mail-notify] 发现新邮件: accountId={}, accountName={}, count={}",
                accountId, account.getName(), messages.size());
    }

    /**
     * 通过统一 SSE 通道广播新邮件（与 Socket 推送并存，前端切换到单连接）。
     *
     * <p>载荷与 Socket 契约一致：{@code {accountId, accountName, count, latest}}，
     * 事件名 {@code new-mail}。</p>
     *
     * @param account 账号
     * @param count   新邮件条数
     * @param latest  最新一封
     */
    private void publishSse(SysAccount account, int count, MailboxMessage latest) {
        Map<String, Object> data = new LinkedHashMap<>(6);
        data.put("accountId", account.getAccountId());
        data.put("accountName", account.getName());
        data.put("count", count);
        data.put("latest", latest);
        try {
            eventSseService.broadcast("new-mail", data);
        } catch (Exception e) {
            // 推送失败不影响轮询本身
            log.warn("[mail-notify] SSE 推送新邮件失败: accountId={}, error={}", account.getAccountId(), e.getMessage());
        }
    }
}
