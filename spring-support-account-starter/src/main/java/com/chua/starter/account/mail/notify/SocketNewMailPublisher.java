package com.chua.starter.account.mail.notify;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.common.support.text.json.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 新邮件 Socket 推送发布者。
 *
 * <p>轮询器发现新邮件后，通过本类把事件推给前端：前端
 * {@code subscribe("account", "new-mail")} 即可收到推送，配合浏览器
 * Notification 与未读徽标使用。</p>
 *
 * <p>整个类被 {@code @ConditionalOnClass(SocketSessionTemplate.class)} 兜底：
 * 宿主未引入 spring-support-socket-starter 时 Bean 不创建，推送静默降级，
 * 不向宿主塞任何 Controller、定时任务或表迁移（R5）。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(SocketSessionTemplate.class)
public class SocketNewMailPublisher {

    private final SocketSessionTemplate template;

    /**
     * 发布新邮件通知。
     *
     * <p>统一广播到所有在线客户端，由前端按 accountId 自行过滤；推送失败
     * 不影响轮询本身。</p>
     *
     * <p><b>消息契约</b>：必须用 {@code "message"} 事件广播，载荷为
     * {@code {module, event, data, timestamp}}——前端 {@code subscribe("account", "new-mail")}
     * 按 {@code module + event} 分发，字段平铺或换事件名都会收不到。</p>
     *
     * @param accountId  产生新邮件的账号标识
     * @param accountName 账号名称（展示用）
     * @param count      新邮件条数
     * @param latest     最新一封（含主题、发件人、发送时间）
     */
    public void publish(String accountId, String accountName, int count, MailboxMessage latest) {
        if (template == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>(6);
        data.put("accountId", accountId);
        data.put("accountName", accountName);
        data.put("count", count);
        data.put("latest", latest);
        Map<String, Object> payload = new LinkedHashMap<>(6);
        payload.put("module", "account");
        payload.put("event", "new-mail");
        payload.put("data", data);
        payload.put("timestamp", System.currentTimeMillis());
        String json = Json.toJson(payload);
        try {
            template.broadcast("message", json);
            log.debug("[mail-notify] 推送新邮件: accountId={}, count={}", accountId, count);
        } catch (Exception e) {
            // 推送失败不影响轮询本身
            log.warn("[mail-notify] 推送新邮件失败: accountId={}, error={}", accountId, e.getMessage());
        }
    }
}
