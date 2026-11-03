package com.chua.starter.channel.tempaccount;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.datasearch.sms.model.SmsPhoneInfo;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.channel.ChannelRegistry;
import com.chua.starter.channel.spi.ChannelKind;
import com.chua.starter.channel.spi.TemporaryAccountProvider;
import com.chua.starter.channel.tempaccount.mapper.SysAiChannelTempIdentityMapper;
import com.chua.starter.channel.tempaccount.mapper.SysAiChannelTempMessageMapper;
import com.chua.starter.channel.tempaccount.model.TempIdentity;
import com.chua.starter.channel.tempaccount.store.SysAiChannelTempIdentity;
import com.chua.starter.channel.tempaccount.store.SysAiChannelTempMessage;
import com.chua.starter.channel.template.MessageTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 临时账号服务 —— 随机虚拟身份的创建、查询、删除与增量消息轮询。
 *
 * <p>一个临时身份 = 一个短信渠道（接码）+ 一个邮件渠道（收信），可只其一。
 * 渠道从 {@link ChannelRegistry} 取（按 {@link ChannelKind} 匹配默认渠道），
 * 取代原先对 {@code freephonenum} / {@code mail-tm} 的硬编码；
 * 领取身份调用 {@link TemporaryAccountProvider#acquire()}，收消息调用
 * {@link TemporaryAccountProvider#fetchMessages}。</p>
 *
 * <p><b>存储</b>：内存缓存（{@code ConcurrentHashMap}）用于列表快照与 SSE 增量；
 * 同时「落库」到 {@code sys_ai_channel_temp_identity} / {@code sys_ai_channel_temp_message}，
 * 进程重启后从库恢复身份与最近消息（消息按 {@code (identity_id, fingerprint)} 唯一索引去重）。</p>
 *
 * <p><b>调度</b>：与 {@code ChannelCheckinTask} 同范式，动态 {@code Trigger} 读
 * {@code sys_ai_global_setting} 的 {@code global} 分组：开关
 * {@code enable_temp_account_poll}（默认打开）、间隔
 * {@code temp_account_poll_interval_sec}（秒，最小 15s，默认 15s）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TempAccountService implements SchedulingConfigurer {

    /**
     * 全局设置分组
     */
    private static final String GROUP = "global";

    /**
     * 轮询开关设置键
     */
    private static final String SETTING_ENABLE = "enable_temp_account_poll";

    /**
     * 轮询间隔设置键
     */
    private static final String SETTING_INTERVAL_SEC = "temp_account_poll_interval_sec";

    /**
     * 默认轮询间隔（秒）
     */
    private static final long DEFAULT_INTERVAL_SEC = 15L;

    /**
     * 最小轮询间隔（秒）
     */
    private static final long MIN_INTERVAL_SEC = 15L;

    /**
     * 消息种类：短信
     */
    private static final String KIND_SMS = "SMS";

    /**
     * 消息种类：邮件
     */
    private static final String KIND_EMAIL = "EMAIL";

    /**
     * 已创建的临时身份（内存态）
     */
    private final Map<String, TempIdentity> identities = new ConcurrentHashMap<>();

    /**
     * SSE 广播注册表
     */
    private final TempAccountStreamRegistry streamRegistry;

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 临时身份映射器
     */
    private final SysAiChannelTempIdentityMapper identityMapper;

    /**
     * 临时身份消息映射器
     */
    private final SysAiChannelTempMessageMapper messageMapper;

    /**
     * JSON 序列化器（身份载荷回填号码画像用）
     */
    private final ObjectMapper objectMapper;

    /**
     * 短信 / 邮件模板服务：给轮询到的新消息补渲染结果（display）
     */
    private final MessageTemplateService templateService;

    /**
     * 注册动态轮询任务，每次执行后按配置间隔计算下次执行时间。
     *
     * @param taskRegistrar 调度任务注册器
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(this::pollMessages, this::nextTriggerTime);
    }

    /**
     * 启动时从库恢复临时身份与最近消息（进程重启不丢）。
     *
     * <p>表可能尚未创建（首次升级），恢复失败只告警不阻断启动。</p>
     */
    @PostConstruct
    public void restore() {
        try {
            List<SysAiChannelTempIdentity> rows = identityMapper.selectList(null);
            if (rows == null || rows.isEmpty()) {
                return;
            }
            for (SysAiChannelTempIdentity row : rows) {
                TempIdentity identity = TempIdentity.builder()
                        .id(row.getIdentityId())
                        .smsChannel(row.getSmsChannel())
                        .emailChannel(row.getEmailChannel())
                        .userId(row.getUserId())
                        .createdAt(row.getCreatedAt() == null ? System.currentTimeMillis() : row.getCreatedAt())
                        .build();
                applySmsIdentity(identity, row.getSmsIdentity());
                applyEmailIdentity(identity, row.getEmailIdentity());
                loadMessages(identity);
                identities.put(identity.getId(), identity);
            }
            log.info("[temp-account] 从库恢复 {} 个临时身份", identities.size());
        } catch (Exception e) {
            log.warn("[temp-account] 恢复临时身份失败（表可能未创建）: {}", e.getMessage());
        }
    }

    /**
     * 随机创建一个临时身份。
     *
     * <p>按 {@code type} 有选择地随机生成通道：{@code sms}（仅虚拟号码）、
     * {@code email}（仅临时邮箱）、{@code both}（号码 + 邮箱，默认）。
     * 指定通道生成失败（渠道不可用）时该通道置空，身份仍创建成功。</p>
     *
     * @param type 通道类型：sms / email / both
     * @return 新身份
     */
    public TempIdentity create(String type) {
        return create(type, null, null);
    }

    /**
     * 随机创建一个临时身份，并支持指定短信 / 邮件渠道。
     *
     * <p>渠道选择遵循「指定优先 + 优先级降级」：传了 {@code smsChannel} / {@code emailChannel}
     * 且该渠道已注册并匹配种类时直接使用；未传或不可用时回落到该种类中
     * {@code priority()} 最高的渠道（见 {@code ChannelRegistry.resolveTemporary}）。</p>
     *
     * @param type       通道类型：sms / email / both
     * @param smsChannel 期望的短信渠道标识（可空）
     * @param emailChannel 期望的邮件渠道标识（可空）
     * @return 新身份
     */
    public TempIdentity create(String type, String smsChannel, String emailChannel) {
        boolean wantSms = type == null || type.isBlank() || "both".equalsIgnoreCase(type) || "sms".equalsIgnoreCase(type);
        boolean wantEmail = type == null || type.isBlank() || "both".equalsIgnoreCase(type) || "email".equalsIgnoreCase(type);

        TempIdentity identity = TempIdentity.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .userId(AiUserContext.currentUserId())
                .createdAt(System.currentTimeMillis())
                .build();

        if (wantSms) {
            acquireSms(identity, smsChannel);
        }
        if (wantEmail) {
            acquireEmail(identity, emailChannel);
        }

        identities.put(identity.getId(), identity);
        persistIdentity(identity);
        log.info("[temp-account] 创建临时身份 {} 号码={} 邮箱={}",
                identity.getId(), identity.getPhone() == null ? "-" : identity.getPhone().getPhone(),
                identity.getEmail() == null ? "-" : identity.getEmail());

        // 建号立即广播，前端列表实时出现
        streamRegistry.broadcast(TempAccountStreamRegistry.EVENT_ACCOUNT, toView(identity));
        // 首轮立即抓一次历史短信/邮件
        try {
            poll(identity);
        } catch (Exception e) {
            log.debug("[temp-account] 首轮抓取跳过: {}", e.getMessage());
        }
        return identity;
    }

    /**
     * 查询全部临时身份（列表轻量视图，不含历史消息与内部去重集合）。
     *
     * @return 身份视图列表（按创建时间倒序）
     */
    public List<Map<String, Object>> list() {
        return identities.values().stream()
                .sorted(Comparator.comparingLong(TempIdentity::getCreatedAt).reversed())
                .map(this::toView)
                .toList();
    }

    /**
     * 查询单个临时身份详情（含全部短信与邮件）。
     *
     * @param id 身份标识
     * @return 身份详情；不存在返回 null
     */
    public TempIdentity detail(String id) {
        return identities.get(id);
    }

    /**
     * 删除临时身份（同时清理落库的身份与消息）。
     *
     * @param id 身份标识
     * @return true 删除成功
     */
    public boolean delete(String id) {
        TempIdentity removed = identities.remove(id);
        boolean dbRemoved = false;
        try {
            dbRemoved = identityMapper.deleteById(id) > 0;
            messageMapper.delete(new LambdaQueryWrapper<SysAiChannelTempMessage>()
                    .eq(SysAiChannelTempMessage::getIdentityId, id));
        } catch (Exception e) {
            log.warn("[temp-account] 身份 {} 落库数据清理失败: {}", id, e.getMessage());
        }
        if (removed == null && !dbRemoved) {
            return false;
        }
        log.info("[temp-account] 删除临时身份 {}", id);
        streamRegistry.broadcast(TempAccountStreamRegistry.EVENT_REMOVE,
                Map.of("id", id, "phone", removed == null || removed.getPhone() == null
                        ? "" : nullToEmpty(removed.getPhone().getPhone())));
        return true;
    }

    /**
     * 定时轮询：抓取所有身份的新短信 / 新邮件并广播增量。
     */
    public void pollMessages() {
        if (!isEnabled() || identities.isEmpty()) {
            return;
        }
        for (TempIdentity identity : identities.values()) {
            try {
                poll(identity);
            } catch (Exception e) {
                log.debug("[temp-account] 轮询身份 {} 失败: {}", identity.getId(), e.getMessage());
            }
        }
    }

    /**
     * 计算下次执行时间：读全局配置间隔（最小 15s），未配置时用默认值。
     *
     * @param triggerContext 触发器上下文
     * @return 下次执行时间
     */
    private Instant nextTriggerTime(TriggerContext triggerContext) {
        long intervalSec = settingReader.getInt(GROUP, SETTING_INTERVAL_SEC, (int) DEFAULT_INTERVAL_SEC);
        if (intervalSec < MIN_INTERVAL_SEC) {
            intervalSec = MIN_INTERVAL_SEC;
        }
        Date last = triggerContext.lastScheduledExecutionTime();
        if (last == null) {
            return Instant.now().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
        }
        return last.toInstant().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
    }

    /**
     * 临时身份轮询是否启用（全局分组 {@code enable_temp_account_poll}，默认启用）。
     *
     * @return true 表示启用
     */
    private boolean isEnabled() {
        return settingReader.getBool(GROUP, SETTING_ENABLE, true);
    }

    /**
     * 从渠道注册表领取一个随机虚拟号码（SMS 渠道）。
     *
     * @param identity   目标身份
     * @param preferred  期望的渠道标识（可空，走优先级降级）
     */
    private void acquireSms(TempIdentity identity, String preferred) {
        TemporaryAccountProvider provider = ChannelRegistry.resolveTemporary(ChannelKind.SMS, preferred);
        if (provider == null) {
            log.warn("[temp-account] 未找到可用的短信接码渠道");
            return;
        }
        try {
            Map<String, Object> payload = provider.acquire();
            if (payload == null) {
                log.warn("[temp-account] 短信渠道 {} 领取号码失败", provider.channel());
                return;
            }
            identity.setSmsChannel(provider.channel());
            identity.setSmsProvider(asString(payload.get("provider")));
            identity.setPhone(toPhoneInfo(payload.get("phone")));
        } catch (Exception e) {
            log.warn("[temp-account] 短信渠道 {} 领取号码异常: {}", provider.channel(), e.getMessage());
        }
    }

    /**
     * 从渠道注册表创建一个临时邮箱（EMAIL 渠道）。
     *
     * @param identity   目标身份
     * @param preferred  期望的渠道标识（可空，走优先级降级）
     */
    private void acquireEmail(TempIdentity identity, String preferred) {
        TemporaryAccountProvider provider = ChannelRegistry.resolveTemporary(ChannelKind.EMAIL, preferred);
        if (provider == null) {
            log.warn("[temp-account] 未找到可用的邮件收信渠道");
            return;
        }
        try {
            Map<String, Object> payload = provider.acquire();
            Object email = payload == null ? null : payload.get("email");
            if (email == null || email.toString().isBlank()) {
                log.warn("[temp-account] 邮件渠道 {} 创建邮箱失败", provider.channel());
                return;
            }
            identity.setEmailChannel(provider.channel());
            identity.setEmailProvider(asString(payload.get("provider")));
            identity.setEmail(email.toString());
        } catch (Exception e) {
            log.warn("[temp-account] 邮件渠道 {} 创建邮箱异常: {}", provider.channel(), e.getMessage());
        }
    }

    /**
     * 抓取单个身份的新消息（增量，去重后仅广播与落库新增）。
     *
     * @param identity 身份
     */
    private void poll(TempIdentity identity) {
        List<Map<String, Object>> newSms = new ArrayList<>();
        List<Map<String, Object>> newEmails = new ArrayList<>();

        // 1. 短信：抓取该号码的全部短信，与已见指纹对比取新增
        if (identity.getPhone() != null && identity.getSmsChannel() != null) {
            TemporaryAccountProvider provider = ChannelRegistry.temporaryChannels().get(identity.getSmsChannel());
            if (provider != null) {
                try {
                    List<Map<String, Object>> messages = provider.fetchMessages(identity.smsIdentity());
                    if (messages != null) {
                        for (Map<String, Object> m : messages) {
                            String fp = smsFingerprint(m);
                            if (identity.getSeenSms().add(fp)) {
                                newSms.add(m);
                                persistMessage(identity, KIND_SMS, fp, m);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("[temp-account] 身份 {} 短信抓取失败: {}", identity.getId(), e.getMessage());
                }
            }
        }

        // 2. 邮件：抓取邮箱最新邮件，与已见指纹对比取新增
        if (identity.getEmail() != null && identity.getEmailChannel() != null) {
            TemporaryAccountProvider provider = ChannelRegistry.temporaryChannels().get(identity.getEmailChannel());
            if (provider != null) {
                try {
                    List<Map<String, Object>> messages = provider.fetchMessages(identity.emailIdentity());
                    if (messages != null) {
                        for (Map<String, Object> m : messages) {
                            String fp = emailFingerprint(m);
                            if (identity.getSeenEmail().add(fp)) {
                                newEmails.add(m);
                                persistMessage(identity, KIND_EMAIL, fp, m);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("[temp-account] 身份 {} 邮件抓取失败: {}", identity.getId(), e.getMessage());
                }
            }
        }

        if (newSms.isEmpty() && newEmails.isEmpty()) {
            return;
        }

        // 模板装饰：用该种类的默认模板渲染展示文本，短信 / 邮箱组件优先读 display
        String phone = identity.getPhone() == null ? "" : nullToEmpty(identity.getPhone().getPhone());
        templateService.decorate(KIND_SMS, newSms, Map.of("phone", phone));
        templateService.decorate(KIND_EMAIL, newEmails, Map.of("email", nullToEmpty(identity.getEmail())));

        // 合并进缓存（倒序：最新在前）
        synchronized (identity) {
            newSms.forEach(m -> identity.getSmsMessages().add(0, m));
            newEmails.forEach(m -> identity.getEmails().add(0, m));
        }

        Map<String, Object> payload = Map.of(
                "id", identity.getId(),
                "sms", newSms,
                "emails", newEmails);
        int delivered = streamRegistry.broadcast(TempAccountStreamRegistry.EVENT_MESSAGE, payload);
        log.info("[temp-account] 身份 {} 新短信 {} 条 / 新邮件 {} 封，送达 {} 连接",
                identity.getId(), newSms.size(), newEmails.size(), delivered);
    }

    /**
     * 身份落库（唯一主键，重复插入时跳过）。
     *
     * @param identity 身份
     */
    private void persistIdentity(TempIdentity identity) {
        try {
            SysAiChannelTempIdentity row = new SysAiChannelTempIdentity();
            row.setIdentityId(identity.getId());
            row.setSmsChannel(identity.getSmsChannel());
            row.setSmsIdentity(identity.getPhone() == null ? null : identity.smsIdentity());
            row.setEmailChannel(identity.getEmailChannel());
            row.setEmailIdentity(identity.getEmail() == null ? null : identity.emailIdentity());
            row.setUserId(identity.getUserId());
            row.setCreatedAt(identity.getCreatedAt());
            identityMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.debug("[temp-account] 身份 {} 已存在，跳过落库", identity.getId());
        } catch (Exception e) {
            log.warn("[temp-account] 身份 {} 落库失败: {}", identity.getId(), e.getMessage());
        }
    }

    /**
     * 消息增量落库（唯一索引 {@code (identity_id, fingerprint)} 天然去重）。
     *
     * @param identity    所属身份
     * @param kind        消息种类（SMS / EMAIL）
     * @param fingerprint 去重指纹
     * @param payload     消息载荷
     */
    private void persistMessage(TempIdentity identity, String kind, String fingerprint, Map<String, Object> payload) {
        try {
            SysAiChannelTempMessage row = new SysAiChannelTempMessage();
            row.setMessageId(UUID.randomUUID().toString().replace("-", ""));
            row.setIdentityId(identity.getId());
            row.setKind(kind);
            row.setFingerprint(fingerprint);
            row.setPayload(payload);
            row.setCreatedAt(System.currentTimeMillis());
            messageMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.debug("[temp-account] 消息指纹重复，跳过落库: {}", fingerprint);
        } catch (Exception e) {
            log.warn("[temp-account] 消息落库失败: {}", e.getMessage());
        }
    }

    /**
     * 从库回填短信身份（号码画像 + 数据源名）。
     *
     * @param identity 目标身份
     * @param payload  身份载荷 JSON
     */
    private void applySmsIdentity(TempIdentity identity, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        identity.setSmsProvider(asString(payload.get("provider")));
        identity.setPhone(toPhoneInfo(payload.get("phone")));
    }

    /**
     * 从库回填邮件身份（邮箱地址 + 数据源名）。
     *
     * @param identity 目标身份
     * @param payload  身份载荷 JSON
     */
    private void applyEmailIdentity(TempIdentity identity, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        identity.setEmailProvider(asString(payload.get("provider")));
        Object email = payload.get("email");
        identity.setEmail(email == null ? null : email.toString());
    }

    /**
     * 从库加载该身份的历史消息（按时间升序取，倒序插入内存，最新在前）。
     *
     * @param identity 目标身份
     */
    private void loadMessages(TempIdentity identity) {
        List<SysAiChannelTempMessage> rows = messageMapper.selectList(
                new LambdaQueryWrapper<SysAiChannelTempMessage>()
                        .eq(SysAiChannelTempMessage::getIdentityId, identity.getId())
                        .orderByAsc(SysAiChannelTempMessage::getCreatedAt));
        if (rows == null) {
            return;
        }
        for (SysAiChannelTempMessage row : rows) {
            Map<String, Object> payload = row.getPayload();
            if (payload == null) {
                continue;
            }
            if (KIND_SMS.equals(row.getKind())) {
                identity.getSmsMessages().add(0, payload);
                identity.getSeenSms().add(row.getFingerprint());
            } else if (KIND_EMAIL.equals(row.getKind())) {
                identity.getEmails().add(0, payload);
                identity.getSeenEmail().add(row.getFingerprint());
            }
        }
    }

    /**
     * 计算短信指纹（发件人 + 内容），用于去重。
     *
     * @param m 短信载荷
     * @return 指纹
     */
    private static String smsFingerprint(Map<String, Object> m) {
        return asString(m.get("from")) + "|" + asString(m.get("content"));
    }

    /**
     * 计算邮件指纹（id 优先，缺失则主题 + 发件人），用于去重。
     *
     * @param m 邮件载荷
     * @return 指纹
     */
    private static String emailFingerprint(Map<String, Object> m) {
        String id = asString(m.get("id"));
        if (!id.isBlank()) {
            return "id:" + id;
        }
        return "sig:" + asString(m.get("subject")) + "|" + asString(m.get("from"));
    }

    /**
     * 转列表视图（供前端渲染，字段与历史临时账号契约保持一致）。
     *
     * @param identity 身份
     * @return 视图载荷
     */
    private Map<String, Object> toView(TempIdentity identity) {
        Map<String, Object> phone = null;
        if (identity.getPhone() != null) {
            SmsPhoneInfo p = identity.getPhone();
            phone = new LinkedHashMap<>(4);
            phone.put("phone", nullToEmpty(p.getPhone()));
            phone.put("countryCode", nullToEmpty(p.getCountryCode()));
            phone.put("countryName", nullToEmpty(p.getCountryName()));
            phone.put("smsUrl", nullToEmpty(p.getSmsUrl()));
        }
        Map<String, Object> view = new LinkedHashMap<>(12);
        view.put("id", identity.getId());
        view.put("smsProvider", nullToEmpty(identity.getSmsProvider()));
        view.put("emailProvider", nullToEmpty(identity.getEmailProvider()));
        // 渠道标识：前端据此显示来源与「进入优先级」提示
        view.put("smsChannel", nullToEmpty(identity.getSmsChannel()));
        view.put("emailChannel", nullToEmpty(identity.getEmailChannel()));
        view.put("email", nullToEmpty(identity.getEmail()));
        view.put("createdAt", identity.getCreatedAt());
        view.put("smsCount", identity.getSmsMessages().size());
        view.put("emailCount", identity.getEmails().size());
        view.put("phone", phone);
        return view;
    }

    /**
     * 把身份载荷里的号码画像转换成 {@link SmsPhoneInfo}。
     *
     * @param value 号码画像（{@link SmsPhoneInfo} 或 Map）
     * @return 号码画像；空返回 {@code null}
     */
    private SmsPhoneInfo toPhoneInfo(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof SmsPhoneInfo info) {
            return info;
        }
        return objectMapper.convertValue(value, SmsPhoneInfo.class);
    }

    /**
     * 对象转字符串。
     *
     * @param value 对象
     * @return 字符串；空返回空串
     */
    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * 空值转空串。
     *
     * @param value 字符串
     * @return 字符串；空返回空串
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
