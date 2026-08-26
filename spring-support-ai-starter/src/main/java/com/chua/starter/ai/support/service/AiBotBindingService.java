package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.client.ClientRequest;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClient;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.mapper.SysAiBotBindingMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 iLink Bot 绑定服务。
 *
 * <p>管理多个 Bot 绑定记录的增删改查与扫码登录。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBotBindingService {

    /** iLink Bot 官方 API 基础地址 */
    private static final String ILINK_BASE = "https://ilinkai.weixin.qq.com";

    /** Bot 绑定 Mapper */
    private final SysAiBotBindingMapper botBindingMapper;

    /** 扫码会话内存表（key -> 会话状态）。生产环境可替换为 Redis。 */
    private final ConcurrentHashMap<String, QrSession> qrSessions = new ConcurrentHashMap<>();

    // ==================== 绑定 CRUD ====================

    /**
     * 获取全部绑定列表（按创建时间降序）。
     */
    public List<SysAiBotBinding> listAll() {
        return botBindingMapper.selectList(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .orderByDesc(SysAiBotBinding::getCreateTime));
    }

    public SysAiBotBinding getById(Long id) {
        return botBindingMapper.selectById(id);
    }

    public SysAiBotBinding findByBotId(String botId) {
        return botBindingMapper.selectOne(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .eq(SysAiBotBinding::getBotId, botId));
    }

    public Long saveOrUpdate(SysAiBotBinding binding) {
        SysAiBotBinding existing = findByBotId(binding.getBotId());
        if (existing != null) {
            binding.setId(existing.getId());
            binding.setUpdateTime(LocalDateTime.now());
            botBindingMapper.updateById(binding);
            return existing.getId();
        }
        binding.setCreateTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        botBindingMapper.insert(binding);
        return binding.getId();
    }

    public void updateStatus(String botId, String status) {
        SysAiBotBinding binding = findByBotId(botId);
        if (binding != null) {
            binding.setStatus(status);
            binding.setLastActiveTime(LocalDateTime.now());
            binding.setUpdateTime(LocalDateTime.now());
            botBindingMapper.updateById(binding);
        }
    }

    public void delete(Long id) {
        botBindingMapper.deleteById(id);
    }

    // ==================== iLink 扫码登录 ====================

    /**
     * 生成 iLink 扫码会话。
     *
     * <p>调用 {@code GET /ilink/bot/get_bot_qrcode} 获取二维码，
     * 前端通过 {@link #checkQrCodeStatus(String)} 轮询确认状态，
     * 扫码成功后自动将凭证写入 {@code sys_ai_bot_binding}。</p>
     *
     * @return 会话 key + 二维码图片 URL
     */
    public QrResult generateQrCode(String platform) {
        String key = UUID.randomUUID().toString();
        QrSession session = new QrSession();
        session.key = key;
        session.platform = platform;
        session.status = "pending";
        session.expireAt = System.currentTimeMillis() + 180_000L; // 3 分钟

        qrSessions.put(key, session);

        // 同步调用 iLink API（最多等 10s），确保返回前二维码已就绪；
        // bot_type=3 仅对应个人微信，企业微信（wecom）iLink 暂不支持扫码。
        if ("wecom".equals(platform)) {
            session.status = "error";
            session.errorMsg = "企业微信不支持 iLink 扫码绑定";
            log.warn("[Bot] wecom 扫码被拒绝 key={}", key);
            return new QrResult(key, "");
        }
        try {
            Map<String, Object> qrResp = iLinkGet("/ilink/bot/get_bot_qrcode?bot_type=3");
            String qrCode = str(qrResp, "qrcode");
            String qrImgUrl = str(qrResp, "qrcode_img_content");
            if (qrCode != null && !qrCode.isBlank()) {
                session.qrKey = qrCode;
                session.qrImageUrl = qrImgUrl;
                log.info("[Bot] 二维码已生成 key={} imgUrl={}", key, qrImgUrl);
            } else {
                session.status = "error";
                session.errorMsg = "获取二维码失败";
                log.warn("[Bot] 获取二维码返回空 key={}", key);
            }
        } catch (Exception e) {
            session.status = "error";
            session.errorMsg = e.getMessage();
            log.error("[Bot] 生成二维码异常 key={}", key, e);
        }

        return new QrResult(key, session.qrImageUrl != null ? session.qrImageUrl : "");
    }

    /**
     * 轮询扫码会话状态（前端每 3 秒调用一次）。
     *
     * <p>iLink 服务端对 get_qrcode_status 做长轮询（最长 35s 阻塞），
     * 直接透传响应即可；confirmed 时返回 bot_token 与 bot_id。</p>
     *
     * @param key 会话 key
     * @return 状态 + 凭证（confirmed 时）
     */
    public QrStatus checkQrCodeStatus(String key) {
        QrSession session = qrSessions.get(key);
        if (session == null) {
            throw new IllegalArgumentException("key 无效或已过期");
        }
        // 懒清理过期 session
        if (System.currentTimeMillis() > session.expireAt && !"confirmed".equals(session.status)) {
            session.status = "expired";
            qrSessions.remove(key);
        }
        if ("confirmed".equals(session.status) && session.botId != null) {
            return new QrStatus("confirmed", session.botId, session.botToken);
        }
        // 长轮询：透传 iLink 响应（含 status: wait/scanned/confirmed/expired）
        try {
            Map<String, Object> resp = iLinkGet(
                    "/ilink/bot/get_qrcode_status?qrcode=" + java.net.URLEncoder.encode(session.qrKey, "UTF-8"));
            String status = str(resp, "status");
            if (status != null) {
                session.status = status;
            }
            if ("confirmed".equals(status)) {
                session.botId = str(resp, "botId");
                session.botToken = str(resp, "botToken");
                String userId = str(resp, "userId");
                log.info("[Bot] 扫码确认 key={} botId={}", key, session.botId);
                // 自动持久化绑定记录
                SysAiBotBinding binding = new SysAiBotBinding();
                binding.setBotId(session.botId);
                binding.setBotToken(session.botToken);
                binding.setPlatform(session.platform != null ? session.platform : "wechat");
                binding.setNickname(userId != null ? userId : session.botId);
                binding.setStatus("online");
                binding.setBindSource("qrcode_scan");
                binding.setCreateTime(LocalDateTime.now());
                binding.setUpdateTime(LocalDateTime.now());
                try {
                    saveOrUpdate(binding);
                    log.info("[Bot] 绑定已写入 DB id={}", binding.getId());
                } catch (Exception e) {
                    log.error("[Bot] 写入绑定失败", e);
                }
                // 从 Map 移除，避免重复轮询
                qrSessions.remove(key);
            } else if ("expired".equals(status)) {
                session.status = "expired";
                qrSessions.remove(key);
            }
            return new QrStatus(status != null ? status : "pending", session.botId, session.botToken);
        } catch (Exception e) {
            log.warn("[Bot] 轮询状态异常 key={}", key, e);
            return new QrStatus("error", null, null);
        }
    }

    // ==================== iLink HTTP 工具 ====================

    private Map<String, Object> iLinkGet(String path) {
        ClientRequest request = ClientRequest.of(ILINK_BASE + path, HttpMethod.GET);
        request.setConnectTimeout(10_000L);
        request.setReadTimeout(60_000L); // iLink 长轮询最长 35s，留足余量
        HttpClient client = HttpClientFactory.getClient();
        ClientResponse response = client.execute(request);
        return parseJson(response.getBodyString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String body) {
        try {
            return Json.fromJson(body,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return null;
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    // ==================== 内部 DTO ====================

    @Data
    static class QrSession {
        String key;
        String platform;
        /** pending / scanned / confirmed / expired / error */
        String status;
        /** iLink 返回的 qrcode key，用于轮询 */
        String qrKey;
        /** 二维码图片 URL（直接 img src） */
        String qrImageUrl;
        String botId;
        String botToken;
        String errorMsg;
        long expireAt;
    }

    @Data
    public static class QrResult {
        /** 轮询 key（前端传给 /qrcode/status?key=） */
        private final String key;
        /** 二维码图片 URL（iLink 直接返回，前端 img src 使用；JSON 字段名 qrcodeUrl） */
        @JsonProperty("qrcodeUrl")
        private final String qrImageUrl;
    }

    @Data
    public static class QrStatus {
        /** pending / scanned / confirmed / expired / error */
        private final String status;
        private final String botId;
        private final String botToken;
    }
}
