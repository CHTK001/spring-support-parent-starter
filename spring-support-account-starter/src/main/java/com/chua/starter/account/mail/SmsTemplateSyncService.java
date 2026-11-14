package com.chua.starter.account.mail;

import com.chua.common.support.task.message.MessagePush;
import com.chua.common.support.task.message.TemplateInfo;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.template.MessageTemplateService;
import com.chua.starter.account.template.store.SysAiChannelMessageTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信模板同步。
 *
 * <p>不绑定厂商：按账号 {@code vendor} 解析 {@link MessagePush} SPI，调用通用的
 * {@link MessagePush#listTemplates()}，把取回的模板按 {@code templateCode} 落到
 * 消息模板表（kind=SMS）。阿里 / 腾讯 / 百度等实现由宿主按需引入。</p>
 *
 * <p><b>能力边界</b>：并非所有通道都会查厂商云端。阿里云实现查
 * {@code QuerySmsTemplateList} 且只保留 {@code AUDIT_PASS} 审核通过的模板；
 * 腾讯 / 百度当前只返回本进程已登记的模板（{@code fetched} 可能为 0），
 * 不代表「云端没有模板」或「同步失败」。界面据此区分提示。</p>
 *
 * <p>同一 code 已存在时更新名称与内容，保留原有默认 / 排序 / 启停设置。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsTemplateSyncService {

    /**
     * 消息模板种类
     */
    private static final String KIND_SMS = "SMS";

    /**
     * 信箱服务（按 vendor 解析短信通道）
     */
    private final MailboxService mailboxService;

    /**
     * 消息模板服务
     */
    private final MessageTemplateService messageTemplateService;

    /**
     * 同步指定短信账号对应厂商的模板。
     *
     * @param accountId 账号标识
     * @return {@code {provider, fetched, created, updated, skipped}}
     */
    public Map<String, Object> sync(String accountId) {
        MessagePush push = mailboxService.smsPush(accountId);
        List<TemplateInfo> remote;
        try {
            remote = push.listTemplates();
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "读取短信模板失败：" + rootMessage(e), e);
        }
        if (remote == null) {
            remote = new ArrayList<>();
        }
        Map<String, SysAiChannelMessageTemplate> existing = new LinkedHashMap<>();
        for (SysAiChannelMessageTemplate template : messageTemplateService.list(KIND_SMS)) {
            existing.put(template.getTemplateCode(), template);
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (TemplateInfo info : remote) {
            if (info == null || StringUtils.isBlank(info.id()) || StringUtils.isBlank(info.content())) {
                skipped++;
                continue;
            }
            SysAiChannelMessageTemplate target = existing.get(info.id());
            boolean isNew = target == null;
            if (isNew) {
                target = new SysAiChannelMessageTemplate();
                target.setTemplateKind(KIND_SMS);
                target.setTemplateCode(info.id());
                target.setTemplateEnabled(true);
                target.setTemplateDefault(false);
                target.setTemplateSortOrder(0);
            }
            target.setTemplateName(StringUtils.defaultString(info.name(), info.id()));
            target.setTemplateContent(info.content());
            try {
                messageTemplateService.save(target);
            } catch (ApiException e) {
                log.warn("[sms-template] 同步模板失败: code={}, msg={}", info.id(), e.getMessage());
                skipped++;
                continue;
            }
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(5);
        result.put("provider", push.getProvider());
        result.put("fetched", remote.size());
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        log.info("[sms-template] 同步完成: accountId={}, provider={}, fetched={}, created={}, updated={}, skipped={}",
                accountId, push.getProvider(), remote.size(), created, updated, skipped);
        return result;
    }

    /**
     * 取最底层异常信息。
     *
     * @param error 异常
     * @return 可读信息
     */
    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return StringUtils.defaultString(cause.getMessage(), cause.getClass().getSimpleName());
    }
}
