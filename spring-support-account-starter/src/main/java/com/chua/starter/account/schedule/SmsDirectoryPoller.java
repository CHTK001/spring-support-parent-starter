package com.chua.starter.account.schedule;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.mail.MailboxMessage;
import com.chua.starter.account.manage.event.AccountEventSseService;
import com.chua.starter.account.manage.service.ManageAccountService;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.phone.TempPhoneService;
import com.chua.starter.account.phone.TempPhoneSmsStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 短信目录轮询器（PollDirectory 接入，不依赖外部平台）。
 *
 * <p>按固定间隔扫描 {@code plugin.account.sms-directory.dir}（默认
 * {@code ./sms-inbox}）下的 {@code *.json} 文件，格式：</p>
 * <pre>{@code
 * { "to": "13800000000", "from": "10086", "content": "验证码 123456", "sentAt": "2026-11-18T14:00:00+08:00" }
 * }</pre>
 * <p>{@code sentAt} 支持 ISO-8601 或毫秒数，缺省取当前时间。{@code to}
 * 归一化后命中某个临时手机号账号的 {@code username} 即写入内存暂存库，并经
 * {@link AccountEventSseService} 广播 {@code new-sms}；解析成功的文件移入
 * {@code done/}，解析失败的移入 {@code error/}，不重复消费。</p>
 *
 * <p><b>副作用控制（R5）</b>：{@code @ConditionalOnProperty} 按
 * {@code plugin.account.sms-directory.enable} 控制（默认 {@code false}），
 * 开关关闭时 Bean 不注册，定时任务也不会带进宿主。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "plugin.account.sms-directory", name = "enable", havingValue = "true")
public class SmsDirectoryPoller {

    /**
     * JSON 解析器（线程安全，可复用）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 统一账号服务
     */
    private final ManageAccountService accountService;

    /**
     * 账号页统一 SSE 事件服务
     */
    private final AccountEventSseService eventSseService;

    /**
     * 短信落盘目录
     */
    @Value("${plugin.account.sms-directory.dir:./sms-inbox}")
    private String directory;

    /**
     * 开关兜底（Bean 层已由 {@code @ConditionalOnProperty} 控制，此处再防御一次）
     */
    @Value("${plugin.account.sms-directory.enable:false}")
    private boolean enable;

    /**
     * 构造注入。
     *
     * @param accountService   统一账号服务
     * @param eventSseService  账号事件 SSE 服务
     */
    public SmsDirectoryPoller(ManageAccountService accountService, AccountEventSseService eventSseService) {
        this.accountService = accountService;
        this.eventSseService = eventSseService;
    }

    /**
     * 固定延迟扫描短信目录。
     */
    @Scheduled(fixedDelayString = "${plugin.account.sms-directory.interval.ms:5000}")
    public void poll() {
        if (!enable) {
            return;
        }
        File dir = new File(directory);
        File[] files = dir.listFiles(item -> item.isFile()
                && item.getName().toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null || files.length == 0) {
            return;
        }
        Map<String, SysAccount> phones = loadTempPhones();
        if (phones.isEmpty()) {
            return;
        }
        for (File file : files) {
            process(file, phones);
        }
    }

    /**
     * 载入当前所有可收短信的手机号（临时 + 常规，归一化号码 -> 账号）。
     *
     * <p>临时手机号（{@code TEMP_PHONE}）与常规手机号（{@code PHONE}）共用
     * 同一条投递链路：号码落在 {@code username} 上，目录轮询按号码匹配后
     * 写入同一个内存暂存库。</p>
     *
     * @return 号码映射
     */
    private Map<String, SysAccount> loadTempPhones() {
        Map<String, SysAccount> phones = new HashMap<>();
        collectPhones(phones, AccountKind.TEMP_PHONE, true);
        collectPhones(phones, AccountKind.PHONE, false);
        return phones;
    }

    /**
     * 收录一类手机号账号。
     *
     * @param phones   号码映射（输出）
     * @param kind     账号种类
     * @param tempOnly 是否仅收录带临时标记的账号（常规种类全量收录）
     */
    private void collectPhones(Map<String, SysAccount> phones, String kind, boolean tempOnly) {
        try {
            for (SysAccount account : accountService.list(kind, null, null)) {
                if (tempOnly && !TempPhoneService.isTempPhone(account)) {
                    continue;
                }
                if (StringUtils.isBlank(account.getUsername())) {
                    continue;
                }
                phones.put(normalizePhone(account.getUsername()), account);
            }
        } catch (Exception e) {
            log.warn("[sms-directory] 读取手机号账号失败: kind={}, error={}", kind, e.getMessage());
        }
    }

    /**
     * 处理单个短信文件：解析 -> 投递 -> 归档。
     *
     * @param file   短信文件
     * @param phones 号码映射
     */
    private void process(File file, Map<String, SysAccount> phones) {
        JsonNode node;
        try {
            node = MAPPER.readTree(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[sms-directory] 短信文件解析失败: file={}, error={}", file.getName(), e.getMessage());
            archive(file, "error");
            return;
        }
        String to = normalizePhone(node.path("to").asText(null));
        SysAccount account = to == null ? null : phones.get(to);
        if (account == null) {
            // 非本机临时号消费的短信：同样归档，避免反复扫描
            log.debug("[sms-directory] 短信未命中临时手机号: file={}, to={}", file.getName(), to);
            archive(file, "done");
            return;
        }
        String content = node.path("content").asText("");
        if (StringUtils.isBlank(content)) {
            log.warn("[sms-directory] 短信内容为空: file={}", file.getName());
            archive(file, "error");
            return;
        }
        MailboxMessage message = TempPhoneSmsStore.get().append(account.getUsername().trim(),
                StringUtils.trimToNull(node.path("from").asText(null)), content, parseSentAt(node));
        publish(account, message);
        archive(file, "done");
        log.info("[sms-directory] 收到新短信: accountId={}, from={}, file={}",
                account.getAccountId(), message.getFrom(), file.getName());
    }

    /**
     * 通过统一 SSE 通道广播新短信。
     *
     * @param account 账号
     * @param message 短信
     */
    private void publish(SysAccount account, MailboxMessage message) {
        Map<String, Object> data = new LinkedHashMap<>(6);
        data.put("accountId", account.getAccountId());
        data.put("accountName", account.getName());
        data.put("count", 1);
        data.put("latest", message);
        try {
            eventSseService.broadcast("new-sms", data);
        } catch (Exception e) {
            // 推送失败不影响投递本身
            log.warn("[sms-directory] 推送新短信失败: accountId={}, error={}", account.getAccountId(), e.getMessage());
        }
    }

    /**
     * 归档文件到 done / error 子目录。
     *
     * @param file       短信文件
     * @param folderName 子目录名
     */
    private void archive(File file, String folderName) {
        try {
            Path parent = file.toPath().toAbsolutePath().getParent();
            Path target = parent.resolve(folderName);
            Files.createDirectories(target);
            Files.move(file.toPath(), target.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("[sms-directory] 短信文件归档失败: file={}, error={}", file.getName(), e.getMessage());
        }
    }

    /**
     * 解析发送时间：毫秒数或 ISO-8601，失败取当前时间。
     *
     * @param node 短信节点
     * @return 毫秒时间戳
     */
    private Long parseSentAt(JsonNode node) {
        JsonNode sentAt = node.path("sentAt");
        if (sentAt.isNumber()) {
            return sentAt.asLong();
        }
        String text = sentAt.asText(null);
        if (StringUtils.isNotBlank(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                // 非毫秒数，继续尝试 ISO-8601
            }
            try {
                return OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli();
            } catch (Exception e) {
                log.debug("[sms-directory] sentAt 无法解析: {}", text);
            }
        }
        return System.currentTimeMillis();
    }

    /**
     * 号码归一化：去非数字，兼容 +86 前缀。
     *
     * @param phone 原始号码
     * @return 归一化号码；空返回 {@code null}
     */
    private String normalizePhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() > 11 && digits.startsWith("86")) {
            digits = digits.substring(2);
        }
        return digits.isEmpty() ? null : digits;
    }
}
