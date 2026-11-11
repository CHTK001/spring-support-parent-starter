package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceWebhookAppKey;
import com.chua.starter.device.support.security.DeviceWebhookDefaults;
import com.chua.starter.device.support.model.AccessRecordPush;
import com.chua.starter.device.support.model.AlarmPush;
import com.chua.starter.device.support.model.AppKeyCreateRequest;
import com.chua.starter.device.support.model.AppKeyView;
import com.chua.starter.device.support.model.WebhookPushResult;
import com.chua.starter.device.support.model.VehiclePassPush;
import com.chua.starter.device.support.service.DeviceWebhookService;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 设备 webhook 接口：appKey 管理 + 门禁记录 / 告警事件入站推送。
 *
 * <p>入站接口无需 monitor 登录态，改用 appKey 签名鉴权；appKey 管理接口仍走登录态，
 * 只对管理员开放。请求体既支持单个对象也支持数组。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/device/webhook")
@RequiredArgsConstructor
public class DeviceWebhookController {

    /** 门禁主题 */
    private static final String TOPIC_ACCESS = "access";
    /** 告警主题 */
    private static final String TOPIC_ALARM = "alarm";
    /** 过车主题 */
    private static final String TOPIC_VEHICLE = "vehicle";

    /** webhook 服务 */
    private final DeviceWebhookService webhookService;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /**
     * 生成 appKey（明文 secret 只返回这一次）。
     *
     * @param request 创建参数；expiresAt 留空为永久有效
     * @return 含 secret 的视图
     */
    @PostMapping("/appkeys")
    public ReturnResult<AppKeyView> createAppKey(@RequestBody(required = false) AppKeyCreateRequest request) {
        return ReturnResult.ok(webhookService.createAppKey(request));
    }

    /**
     * 列出 appKey（不含 secret）。
     *
     * @return 视图列表
     */
    @GetMapping("/appkeys")
    public ReturnResult<List<AppKeyView>> listAppKeys() {
        return ReturnResult.ok(webhookService.listAppKeys());
    }

    /**
     * 停用 appKey。
     *
     * @param id 主键
     * @return 空结果
     */
    @PostMapping("/appkeys/{id}/disable")
    public ReturnResult<Void> disableAppKey(@PathVariable String id) {
        webhookService.disableAppKey(id);
        return ReturnResult.ok();
    }

    /**
     * 删除 appKey。
     *
     * @param id 主键
     * @return 空结果
     */
    @DeleteMapping("/appkeys/{id}")
    public ReturnResult<Void> deleteAppKey(@PathVariable String id) {
        webhookService.deleteAppKey(id);
        return ReturnResult.ok();
    }

    /**
     * 门禁通行记录推送入库。
     *
     * @param appKey    X-App-Key
     * @param secret    X-App-Secret（简化模式）
     * @param timestamp X-Timestamp（签名模式）
     * @param nonce     X-Nonce（签名模式）
     * @param signature X-Signature（签名模式）
     * @param request   HTTP 请求（直接读原始字节，避开框架的请求体解码/签名 advice）
     * @return 入库结果
     */
    @TokenForIgnore
    @PostMapping(DeviceWebhookDefaults.PATH_ACCESS)
    public ReturnResult<WebhookPushResult> pushAccessRecords(
            @RequestHeader(value = "X-App-Key", required = false) String appKey,
            @RequestHeader(value = "X-App-Secret", required = false) String secret,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            HttpServletRequest request) {
        String rawBody = readRawBody(request);
        DeviceWebhookAppKey key = webhookService.verify(appKey, secret, timestamp, nonce,
                signature, rawBody, TOPIC_ACCESS);
        List<AccessRecordPush> items = parseList(rawBody, AccessRecordPush.class);
        return ReturnResult.ok(webhookService.ingestAccess(
                key.getDeviceWebhookAppkeyName(), rawBody, items));
    }

    /**
     * 告警事件推送入库，落库后向生命周期回调广播 ALARM_RECEIVED。
     *
     * @param appKey    X-App-Key
     * @param secret    X-App-Secret（简化模式）
     * @param timestamp X-Timestamp（签名模式）
     * @param nonce     X-Nonce（签名模式）
     * @param signature X-Signature（签名模式）
     * @param request   HTTP 请求（直接读原始字节，避开框架的请求体解码/签名 advice）
     * @return 入库结果
     */
    @TokenForIgnore
    @PostMapping(DeviceWebhookDefaults.PATH_ALARM)
    public ReturnResult<WebhookPushResult> pushAlarms(
            @RequestHeader(value = "X-App-Key", required = false) String appKey,
            @RequestHeader(value = "X-App-Secret", required = false) String secret,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            HttpServletRequest request) {
        String rawBody = readRawBody(request);
        DeviceWebhookAppKey key = webhookService.verify(appKey, secret, timestamp, nonce,
                signature, rawBody, TOPIC_ALARM);
        List<AlarmPush> items = parseList(rawBody, AlarmPush.class);
        return ReturnResult.ok(webhookService.ingestAlarms(
                key.getDeviceWebhookAppkeyName(), rawBody, items));
    }

    /**
     * 过车记录推送入库（车闸 webhook）。
     *
     * @param appKey    X-App-Key
     * @param secret    X-App-Secret（简化模式）
     * @param timestamp X-Timestamp（签名模式）
     * @param nonce     X-Nonce（签名模式）
     * @param signature X-Signature（签名模式）
     * @param request   HTTP 请求
     * @return 入库结果
     */
    @TokenForIgnore
    @PostMapping(DeviceWebhookDefaults.PATH_VEHICLE_PASS)
    public ReturnResult<WebhookPushResult> pushVehiclePasses(
            @RequestHeader(value = "X-App-Key", required = false) String appKey,
            @RequestHeader(value = "X-App-Secret", required = false) String secret,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            HttpServletRequest request) {
        String rawBody = readRawBody(request);
        DeviceWebhookAppKey key = webhookService.verify(appKey, secret, timestamp, nonce,
                signature, rawBody, TOPIC_VEHICLE);
        List<VehiclePassPush> items = parseList(rawBody, VehiclePassPush.class);
        return ReturnResult.ok(webhookService.ingestVehiclePasses(
                key.getDeviceWebhookAppkeyName(), rawBody, items));
    }

    /**
     * 直接读 Servlet 原始请求体。
     *
     * <p>不用 {@code @RequestBody String}：宿主 common-starter 的 RequestBodyAdvice /
     * 参数解析链会把 JSON 包体改写成「逗号分隔的键值对」形态，导致 Jackson 解析失败。
     * webhook 是外部系统入口，本就不该走框架的请求解码与签名链。</p>
     *
     * @param request HTTP 请求
     * @return 原始包体
     */
    // [P3C A20 豁免]
    private static String readRawBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("webhook 请求体读取失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 把包体解析成条目列表：数组直接转，单对象包装成单元素列表。
     *
     * @param rawBody 原始包体
     * @param clazz   条目类型
     * @param <T>     条目类型
     * @return 条目列表
     */
    private <T> List<T> parseList(String rawBody, Class<T> clazz) {
        log.info("[Device][Webhook] 收到包体: {}", rawBody == null ? "null"
        // [P3C A20 豁免]
                : rawBody.substring(0, Math.min(300, rawBody.length())));
        try {
            JsonNode node = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            if (node.isArray()) {
                return objectMapper.convertValue(node,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
            }
            return List.of(objectMapper.treeToValue(node, clazz));
        } catch (Exception ex) {
            throw new IllegalArgumentException("webhook 包体解析失败: " + ex.getMessage(), ex);
        }
    }
}
