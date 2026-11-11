package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.callback.DeviceLifecycleEvent;
import com.chua.starter.device.support.callback.DeviceLifecyclePublisher;
import com.chua.starter.device.support.entity.DeviceAccessRecord;
import com.chua.starter.device.support.entity.DeviceAlarm;
import com.chua.starter.device.support.entity.DeviceWebhookAppKey;
import com.chua.starter.device.support.entity.DeviceWebhookReceipt;
import com.chua.starter.device.support.mapper.DeviceAccessRecordMapper;
import com.chua.starter.device.support.mapper.DeviceAlarmMapper;
import com.chua.starter.device.support.mapper.DeviceWebhookAppKeyMapper;
import com.chua.starter.device.support.mapper.DeviceWebhookReceiptMapper;
import com.chua.starter.device.support.entity.DeviceVehiclePass;
import com.chua.starter.device.support.mapper.DeviceVehiclePassMapper;
import com.chua.starter.device.support.model.AccessRecordPush;
import com.chua.starter.device.support.model.VehiclePassPush;
import com.chua.starter.device.support.model.AlarmPush;
import com.chua.starter.device.support.model.AppKeyCreateRequest;
import com.chua.starter.device.support.model.AppKeyView;
import com.chua.starter.device.support.model.WebhookPushResult;
import com.chua.starter.device.support.service.DeviceWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 设备 webhook 服务实现。
 *
 * <p>门禁记录落既有 {@code device_access_record}，告警落 {@code device_alarm}；
 * 两者都不改表结构，幂等统一由 {@code device_webhook_receipt} 承担。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceWebhookServiceImpl implements DeviceWebhookService {

    /** 门禁主题 */
    private static final String TOPIC_ACCESS = "access";
    /** 告警主题 */
    private static final String TOPIC_ALARM = "alarm";
    /** 过车主题 */
    private static final String TOPIC_VEHICLE = "vehicle";
    /** 默认授权主题 */
    private static final String DEFAULT_SCOPES = "access,alarm,vehicle";
    /** 时间戳防重放窗口（秒） */
    private static final long REPLAY_WINDOW_SECONDS = 300L;
    /** 常用时间格式 */
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** appKey 映射器 */
    private final DeviceWebhookAppKeyMapper appKeyMapper;
    /** 幂等回执映射器 */
    private final DeviceWebhookReceiptMapper receiptMapper;
    /** 门禁记录映射器 */
    private final DeviceAccessRecordMapper accessRecordMapper;
    /** 告警映射器 */
    private final DeviceAlarmMapper alarmMapper;
    /** 过车记录映射器 */
    private final DeviceVehiclePassMapper vehiclePassMapper;
    /** 生命周期回调发布器 */
    private final DeviceLifecyclePublisher lifecyclePublisher;

    /** 安全随机数（生成 secret） */
    private final SecureRandom random = new SecureRandom();

    @Override
    public AppKeyView createAppKey(AppKeyCreateRequest request) {
        String name = request == null || !StringUtils.hasText(request.getName())
                ? "webhook-" + LocalDateTime.now().format(DTF) : request.getName().trim();
        String scopes = request == null || !StringUtils.hasText(request.getScopes())
                ? DEFAULT_SCOPES : request.getScopes().trim();
        byte[] secretBytes = new byte[32];
        random.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        DeviceWebhookAppKey entity = new DeviceWebhookAppKey();
        entity.setDeviceWebhookAppkeyKey(UUID.randomUUID().toString().replace("-", ""));
        entity.setDeviceWebhookAppkeySecret(secret);
        entity.setDeviceWebhookAppkeyName(name);
        entity.setDeviceWebhookAppkeyScopes(scopes);
        entity.setDeviceWebhookAppkeyExpiresAt(request == null ? null : parseExpiry(request.getExpiresAt()));
        entity.setDeviceWebhookAppkeyEnabled(1);
        entity.setDeviceWebhookAppkeyCreatedAt(LocalDateTime.now());
        appKeyMapper.insert(entity);
        AppKeyView view = toView(entity);
        // 明文 secret 只在创建响应里出现一次，列表接口不再回显
        view.setSecret(secret);
        log.info("[Device][Webhook] 生成 appKey: name={}, scopes={}, expiresAt={}",
                name, scopes, entity.getDeviceWebhookAppkeyExpiresAt());
        return view;
    }

    @Override
    public List<AppKeyView> listAppKeys() {
        LambdaQueryWrapper<DeviceWebhookAppKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(DeviceWebhookAppKey::getDeviceWebhookAppkeyCreatedAt);
        return appKeyMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    @Override
    public void disableAppKey(String id) {
        DeviceWebhookAppKey entity = appKeyMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("appKey 不存在: " + id);
        }
        entity.setDeviceWebhookAppkeyEnabled(0);
        appKeyMapper.updateById(entity);
    }

    @Override
    public void deleteAppKey(String id) {
        appKeyMapper.deleteById(id);
    }

    @Override
    public DeviceWebhookAppKey verify(String appKey, String secret, String timestamp, String nonce,
                                      String signature, String rawBody, String topic) {
        if (!StringUtils.hasText(appKey)) {
            throw new IllegalArgumentException("缺少 X-App-Key");
        }
        LambdaQueryWrapper<DeviceWebhookAppKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceWebhookAppKey::getDeviceWebhookAppkeyKey, appKey.trim());
        DeviceWebhookAppKey entity = appKeyMapper.selectOne(wrapper);
        if (entity == null || !Integer.valueOf(1).equals(entity.getDeviceWebhookAppkeyEnabled())) {
            throw new IllegalArgumentException("appKey 无效或已停用");
        }
        LocalDateTime expiresAt = entity.getDeviceWebhookAppkeyExpiresAt();
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            throw new IllegalArgumentException("appKey 已过期");
        }
        String scopes = StringUtils.hasText(entity.getDeviceWebhookAppkeyScopes())
                ? entity.getDeviceWebhookAppkeyScopes() : DEFAULT_SCOPES;
        boolean allowed = false;
        for (String scope : scopes.split(",")) {
            if (topic.equalsIgnoreCase(scope.trim())) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new IllegalArgumentException("appKey 未授权主题: " + topic);
        }
        if (StringUtils.hasText(signature)) {
            verifySignature(entity, timestamp, nonce, signature, rawBody == null ? "" : rawBody);
        } else {
            if (!StringUtils.hasText(secret)
                    || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                    entity.getDeviceWebhookAppkeySecret().getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("appKey 密钥校验失败");
            }
        }
        entity.setDeviceWebhookAppkeyLastUsedAt(LocalDateTime.now());
        appKeyMapper.updateById(entity);
        return entity;
    }

    @Override
    public WebhookPushResult ingestAccess(String source, String rawBody, List<AccessRecordPush> items) {
        WebhookPushResult result = newResult(TOPIC_ACCESS, items);
        for (int i = 0; i < items.size(); i++) {
            AccessRecordPush item = items.get(i);
            try {
                if (item == null || !StringUtils.hasText(item.getDeviceId())) {
                    result.getErrors().add("第 " + (i + 1) + " 条缺少 deviceId");
                    continue;
                }
                String externalId = externalId(source, TOPIC_ACCESS, rawBody, i, item.getExternalId());
                if (receiptExists(source, externalId, TOPIC_ACCESS)) {
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                DeviceAccessRecord entity = new DeviceAccessRecord();
                entity.setDeviceAccessRecordDeviceId(item.getDeviceId());
                entity.setDeviceAccessRecordDoorId(item.getDoorId());
                entity.setDeviceAccessRecordCardNo(item.getCardNo());
                entity.setDeviceAccessRecordUserName(item.getUserName());
                entity.setDeviceAccessRecordEventType(item.getEventType());
                entity.setDeviceAccessRecordDirection(item.getDirection());
                entity.setDeviceAccessRecordPhotoUrl(item.getPhotoUrl());
                entity.setDeviceAccessRecordOccurredAt(parseTime(item.getOccurredAt()));
                accessRecordMapper.insert(entity);
                saveReceipt(source, externalId, TOPIC_ACCESS);
                result.setCreated(result.getCreated() + 1);
            } catch (Exception ex) {
                result.getErrors().add("第 " + (i + 1) + " 条: " + ex.getMessage());
            }
        }
        log.info("[Device][Webhook] 门禁推送入库: source={}, received={}, created={}, skipped={}",
                source, result.getReceived(), result.getCreated(), result.getSkipped());
        return result;
    }

    @Override
    public WebhookPushResult ingestAlarms(String source, String rawBody, List<AlarmPush> items) {
        WebhookPushResult result = newResult(TOPIC_ALARM, items);
        for (int i = 0; i < items.size(); i++) {
            AlarmPush item = items.get(i);
            try {
                String externalId = externalId(source, TOPIC_ALARM, rawBody, i, item == null ? null : item.getExternalId());
                if (item == null || receiptExists(source, externalId, TOPIC_ALARM)) {
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                DeviceAlarm entity = new DeviceAlarm();
                entity.setDeviceAlarmSource(source);
                entity.setDeviceAlarmExternalId(externalId);
                entity.setDeviceAlarmDeviceId(item.getDeviceId());
                entity.setDeviceAlarmChannelNo(item.getChannelNo());
                entity.setDeviceAlarmType(StringUtils.hasText(item.getType()) ? item.getType() : "OTHER");
                entity.setDeviceAlarmLevel(StringUtils.hasText(item.getLevel()) ? item.getLevel() : "info");
                entity.setDeviceAlarmTitle(item.getTitle());
                entity.setDeviceAlarmDetail(item.getDetail());
                entity.setDeviceAlarmStatus("unhandled");
                entity.setDeviceAlarmOccurredAt(parseTime(item.getOccurredAt()));
                entity.setDeviceAlarmCreatedAt(LocalDateTime.now());
                alarmMapper.insert(entity);
                saveReceipt(source, externalId, TOPIC_ALARM);
                result.setCreated(result.getCreated() + 1);
                // 出站：告警入库后通知全部回调 Bean，由下游决定是否推送到别的系统
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("alarmId", entity.getDeviceAlarmId());
                payload.put("deviceId", entity.getDeviceAlarmDeviceId());
                payload.put("type", entity.getDeviceAlarmType());
                payload.put("level", entity.getDeviceAlarmLevel());
                payload.put("title", entity.getDeviceAlarmTitle());
                lifecyclePublisher.publish(DeviceLifecycleEvent.success(
                        DeviceLifecycleEvent.TYPE_ALARM_RECEIVED, entity.getDeviceAlarmDeviceId(), source, payload));
            } catch (Exception ex) {
                result.getErrors().add("第 " + (i + 1) + " 条: " + ex.getMessage());
            }
        }
        log.info("[Device][Webhook] 告警推送入库: source={}, received={}, created={}, skipped={}",
                source, result.getReceived(), result.getCreated(), result.getSkipped());
        return result;
    }

    @Override
    public WebhookPushResult ingestVehiclePasses(String source, String rawBody, List<VehiclePassPush> items) {
        WebhookPushResult result = newResult(TOPIC_VEHICLE, items);
        for (int i = 0; i < items.size(); i++) {
            VehiclePassPush item = items.get(i);
            try {
                if (item == null || !StringUtils.hasText(item.getPlateNo())) {
                    result.getErrors().add("第 " + (i + 1) + " 条缺少 plateNo");
                    continue;
                }
                String externalId = externalId(source, TOPIC_VEHICLE, rawBody, i, item.getExternalId());
                if (receiptExists(source, externalId, TOPIC_VEHICLE)) {
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                DeviceVehiclePass entity = new DeviceVehiclePass();
                entity.setDeviceVehiclePassGateId(item.getGateId());
                entity.setDeviceVehiclePassPlateNo(item.getPlateNo());
                entity.setDeviceVehiclePassDirection(item.getDirection());
                entity.setDeviceVehiclePassTime(parseTime(item.getPassTime()));
                entity.setDeviceVehiclePassPhotoUrl(item.getPhotoUrl());
                entity.setDeviceVehiclePassVehicleType(item.getVehicleType());
                entity.setDeviceVehiclePassCreatedAt(LocalDateTime.now());
                vehiclePassMapper.insert(entity);
                saveReceipt(source, externalId, TOPIC_VEHICLE);
                result.setCreated(result.getCreated() + 1);
            } catch (Exception ex) {
                result.getErrors().add("第 " + (i + 1) + " 条: " + ex.getMessage());
            }
        }
        log.info("[Device][Webhook] 过车推送入库: source={}, received={}, created={}, skipped={}",
                source, result.getReceived(), result.getCreated(), result.getSkipped());
        return result;
    }

    /**
     * 初始化结果对象。
     * @param items 条目
     * @return 结果
     */
    private static WebhookPushResult newResult(String topic, List<?> items) {
        WebhookPushResult result = new WebhookPushResult();
        result.setTopic(topic);
        result.setReceived(items == null ? 0 : items.size());
        result.setErrors(new ArrayList<>());
        return result;
    }

    /**
     * 校验 HMAC 签名与时间戳窗口。
     *
     * @param entity    appKey 实体
     * @param timestamp 秒级时间戳
     * @param nonce     随机串
     * @param signature 客户端签名
     * @param rawBody   原始包体
     */
    private static void verifySignature(DeviceWebhookAppKey entity, String timestamp, String nonce,
                                        String signature, String rawBody) {
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (Exception ex) {
            throw new IllegalArgumentException("缺少或非法 X-Timestamp");
        }
        if (Math.abs(System.currentTimeMillis() / 1000L - ts) > REPLAY_WINDOW_SECONDS) {
            throw new IllegalArgumentException("请求时间戳超出防重放窗口");
        }
        String expected = hmac(entity.getDeviceWebhookAppkeySecret(),
                ts + "\n" + (nonce == null ? "" : nonce) + "\n" + rawBody);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("签名校验失败");
        }
    }

    /**
     * 计算 HMAC-SHA256 十六进制摘要。
     *
     * @param secret  密钥
     * @param message 待签名文本
     * @return 小写十六进制摘要
     */
    private static String hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC 计算失败", ex);
        }
    }

    /**
     * 查询幂等回执是否已存在。
     *
     * @param source     来源
     * @param externalId 外部 ID
     * @param topic      主题
     * @return 已存在返回 true
     */
    private boolean receiptExists(String source, String externalId, String topic) {
        LambdaQueryWrapper<DeviceWebhookReceipt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceWebhookReceipt::getDeviceWebhookReceiptSource, source)
                .eq(DeviceWebhookReceipt::getDeviceWebhookReceiptExternalId, externalId)
                .eq(DeviceWebhookReceipt::getDeviceWebhookReceiptTopic, topic);
        return receiptMapper.selectCount(wrapper) > 0;
    }

    /**
     * 写入幂等回执。
     *
     * @param source     来源
     * @param externalId 外部 ID
     * @param topic      主题
     */
    private void saveReceipt(String source, String externalId, String topic) {
        DeviceWebhookReceipt receipt = new DeviceWebhookReceipt();
        receipt.setDeviceWebhookReceiptSource(source);
        receipt.setDeviceWebhookReceiptExternalId(externalId);
        receipt.setDeviceWebhookReceiptTopic(topic);
        receipt.setDeviceWebhookReceiptCreatedAt(LocalDateTime.now());
        receiptMapper.insert(receipt);
    }

    /**
     * 取幂等键：外部没给就按 (来源, 主题, 包体, 序号) 生成摘要。
     *
     * @param source     来源
     * @param topic      主题
     * @param rawBody    原始包体
     * @param index      序号
     * @param externalId 外部 ID，可空
     * @return 幂等键
     */
    private static String externalId(String source, String topic, String rawBody, int index, String externalId) {
        if (StringUtils.hasText(externalId)) {
            return externalId.trim();
        }
        String raw = source + "|" + topic + "|" + (rawBody == null ? "" : rawBody) + "|" + index;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    /**
     * 解析时间；空值或非法格式回落到当前时间。
     *
     * @param text 时间文本
     * @return 时间
     */
    private static LocalDateTime parseTime(String text) {
        if (!StringUtils.hasText(text)) {
            return LocalDateTime.now();
        }
        String value = text.trim().replace('T', ' ');
        try {
            return LocalDateTime.parse(value, DTF);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ex) {
                return LocalDateTime.now();
            }
        }
    }

    /**
     * 解析过期时间；空值表示永久有效。
     *
     * @param text 时间文本
     * @return 过期时间，可空
     */
    private static LocalDateTime parseExpiry(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return parseTime(text);
    }

    /**
     * 实体转对外视图（不回显 secret）。
     *
     * @param entity 实体
     * @return 视图
     */
    private AppKeyView toView(DeviceWebhookAppKey entity) {
        AppKeyView view = new AppKeyView();
        view.setId(entity.getDeviceWebhookAppkeyId());
        view.setAppKey(entity.getDeviceWebhookAppkeyKey());
        view.setName(entity.getDeviceWebhookAppkeyName());
        view.setScopes(entity.getDeviceWebhookAppkeyScopes());
        view.setExpiresAt(entity.getDeviceWebhookAppkeyExpiresAt());
        view.setEnabled(Integer.valueOf(1).equals(entity.getDeviceWebhookAppkeyEnabled()));
        view.setLastUsedAt(entity.getDeviceWebhookAppkeyLastUsedAt());
        view.setCreatedAt(entity.getDeviceWebhookAppkeyCreatedAt());
        return view;
    }
}
