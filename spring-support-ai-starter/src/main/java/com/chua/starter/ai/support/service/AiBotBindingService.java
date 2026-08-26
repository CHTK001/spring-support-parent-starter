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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 iLink Bot 绑定服务。
 *
 * <p>管理多个 Bot 绑定记录的增删改查与扫码登录。</p>
 *
 * <p>扫码登录流程：
 * <ol>
 *   <li>前端调用 {@code POST /qrcode?platform=wechat}，后端异步请求 iLink API 生成二维码，返回会话 key。</li>
 *   <li>前端每 3s 轮询 {@code GET /qrcode/status?key=X}，透传 iLink 长轮询（最长 35s）。</li>
 *   <li>iLink 返回 status=confirmed 后，后端自动写入 sys_ai_bot_binding，前端刷新列表。</li>
 * </ol>
 * 企业微信（wecom）iLink 协议不支持扫码，Controller 直接返回业务错误码。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBotBindingService {

    /** iLink Bot 官方 API 基础地址（个人微信 bot_type=3） */
    private static final String ILINK_BASE = "https://ilinkai.weixin.qq.com";

    /** Bot 绑定 Mapper */
    private final SysAiBotBindingMapper botBindingMapper;

    /**
     * 扫码会话内存表（key -> 会话状态）。
     *
     * <p>使用 ConcurrentHashMap 避免高并发下 session 互相覆盖；
     * 生产环境替换为 Redis，key 带前缀避免冲突。</p>
     */
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

    /**
     * 创建或更新绑定记录（按 botId 去重）。
     *
     * <p>若 botId 已存在则更新，否则插入新记录。
     * MyBatis Plus NOT_NULL 更新策略：null 字段不会覆盖数据库值（Token 留空即保留原值）。</p>
     */
    public Long saveOrUpdate(SysAiBotBinding binding) {
        var existing = findByBotId(binding.getBotId());
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

    /**
     * 更新 Bot 在线状态（由内部同步任务调用，不对接前端）。
     */
    public void updateStatus(String botId, String status) {
        var binding = findByBotId(botId);
        if (binding == null) {
            return;
        }
        binding.setStatus(status);
        binding.setLastActiveTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        botBindingMapper.updateById(binding);
    }

    public void delete(Long id) {
        botBindingMapper.deleteById(id);
    }

    // ==================== iLink 扫码登录 ====================

    /**
     * 生成 iLink 扫码会话。
     *
     * <p>个人微信（wechat）：异步调用 {@code GET /ilink/bot/get_bot_qrcode?bot_type=3}，
     * 生成二维码并写入会话；前端通过 {@link #checkQrCodeStatus(String)} 轮询确认。</p>
     *
     * <p>企业微信（wecom）：iLink 协议不支持，立即返回空 key，前端检测到后会提示改用手动导入。</p>
     *
     * <p>注意：此方法立即返回，二维码生成在后台线程中完成，
     * 前端需轮询 status=ready 后才拿到 qrcodeUrl。</p>
     *
     * @param platform wechat / wecom
     * @return 会话 key（前端传给 /qrcode/status）+ qrcodeUrl（async 模式下即时为空，轮询后才有）
     */
    public QrResult generateQrCode(String platform) {
        // 企业微信不支持 iLink 扫码，直接拒绝（返回空 key，前端识别后走手动模式）
        if ("wecom".equals(platform)) {
            log.info("[Bot] wecom 扫码被拒绝，使用手动导入");
            return new QrResult(UUID.randomUUID().toString(), "");
        }

        var key = UUID.randomUUID().toString();
        var session = new QrSession();
        session.key = key;
        session.platform = platform;
        session.status = "pending";
        session.expireAt = System.currentTimeMillis() + 180_000L; // 3 分钟超时

        qrSessions.put(key, session);

        // 异步发起 iLink 请求，不阻塞 Tomcat 请求线程
        CompletableFuture.runAsync(() -> {
            try {
                var qrResp = iLinkGet("/ilink/bot/get_bot_qrcode?bot_type=3");
                var ret = intVal(qrResp, "ret", -1);
                if (ret != 0) {
                    session.status = "error";
                    session.errorMsg = str(qrResp, "err_msg") + " (ret=" + ret + ")";
                    log.warn("[Bot] iLink 二维码生成失败 key={} err={}", key, session.errorMsg);
                    return;
                }
                var qrCode = str(qrResp, "qrcode");
                var qrImgUrl = str(qrResp, "qrcode_img_content");
                if (qrCode == null || qrCode.isBlank()) {
                    session.status = "error";
                    session.errorMsg = "iLink 未返回 qrcode 字段";
                    log.warn("[Bot] 二维码响应缺少 qrcode key={}", key);
                    return;
                }
                session.qrKey = qrCode;
                session.qrImageUrl = qrImgUrl;
                session.status = "ready";
                log.info("[Bot] 二维码已生成 key={} imgUrl={}", key, qrImgUrl);
            } catch (Exception e) {
                session.status = "error";
                session.errorMsg = e.getMessage();
                log.error("[Bot] 生成二维码异常 key={}", key, e);
            }
        });

        // 立即返回空 qrcodeUrl，前端轮询 status=ready 时再取
        return new QrResult(key, "");
    }

    /**
     * 轮询扫码会话状态（前端每 3 秒调用一次）。
     *
     * <p>iLink 服务端对 get_qrcode_status 做长轮询（最长 35s 阻塞），
     * 本次 HTTP 请求在此线程上透传，完成后返回。<br>
     * confirmed 时自动将凭证写入 sys_ai_bot_binding。</p>
     *
     * @param key 会话 key
     * @return 状态 + 凭证（confirmed 时）
     */
    public QrStatus checkQrCodeStatus(String key) {
        var session = qrSessions.get(key);
        if (session == null) {
            throw new IllegalArgumentException("key 无效或已过期");
        }
        // 懒清理过期 session
        if (System.currentTimeMillis() > session.expireAt && !"confirmed".equals(session.status)) {
            session.status = "expired";
            qrSessions.remove(key);
            return new QrStatus("expired", null, null, null);
        }
        // 已完成则直接返回缓存结果，不重复请求 iLink
        if ("confirmed".equals(session.status) && session.botId != null) {
            return new QrStatus("confirmed", session.qrImageUrl, session.botId, session.botToken);
        }
        // 尚未就绪（等待 iLink 异步生成二维码，或等待用户扫码）
        if (!"ready".equals(session.status)) {
            return new QrStatus(session.status != null ? session.status : "pending", null, null, null);
        }
        // 长轮询：透传 iLink 响应（阻塞最长 35s，等待用户扫码确认）
        try {
            var encoded = URLEncoder.encode(session.qrKey, StandardCharsets.UTF_8.name());
            var resp = iLinkGet("/ilink/bot/get_qrcode_status?qrcode=" + encoded);
            var ret = intVal(resp, "ret", -1);
            if (ret != 0) {
                log.warn("[Bot] iLink 轮询失败 key={} ret={}", key, ret);
                return new QrStatus("error", null, null, null);
            }
            var status = str(resp, "status");
            if (status == null) {
                return new QrStatus("pending", null, null, null);
            }
            session.status = status;
            if ("confirmed".equals(status)) {
                session.botId = str(resp, "botId");
                session.botToken = str(resp, "botToken");
                var userId = str(resp, "userId");
                log.info("[Bot] 扫码确认 key={} botId={}", key, session.botId);
                saveBindingFromScan(key, session, userId);
                qrSessions.remove(key);
                return new QrStatus("confirmed", session.qrImageUrl, session.botId, session.botToken);
            } else if ("expired".equals(status)) {
                qrSessions.remove(key);
                return new QrStatus("expired", null, null, null);
            }
            return new QrStatus(status, session.qrImageUrl, null, null);
        } catch (Exception e) {
            log.warn("[Bot] 轮询状态异常 key={}", key, e);
            return new QrStatus("error", null, null, null);
        }
    }

    /**
     * 扫码成功后自动持久化绑定记录。
     *
     * <p>将 iLink 返回的 botId、botToken 写入 sys_ai_bot_binding，
     * 绑定来源标记为 qrcode_scan，状态设为 online。</p>
     */
    private void saveBindingFromScan(String key, QrSession session, String userId) {
        try {
            var binding = new SysAiBotBinding();
            binding.setBotId(session.botId);
            binding.setBotToken(session.botToken);
            binding.setPlatform(session.platform != null ? session.platform : "wechat");
            binding.setNickname(userId != null ? userId : session.botId);
            binding.setStatus("online");
            binding.setBindSource("qrcode_scan");
            binding.setCreateTime(LocalDateTime.now());
            binding.setUpdateTime(LocalDateTime.now());
            saveOrUpdate(binding);
            log.info("[Bot] 绑定已写入 DB id={}", binding.getId());
        } catch (Exception e) {
            log.error("[Bot] 写入绑定失败 key={} botId={}", key, session.botId, e);
        }
    }

    // ==================== iLink HTTP 工具 ====================

    /**
     * 发起 iLink GET 请求。
     *
     * <p>readTimeout 设为 60s，覆盖 iLink 长轮询最长 35s 的阻塞；
     * connectTimeout 10s 防止 DNS 解析卡死。</p>
     */
    private Map<String, Object> iLinkGet(String path) {
        var request = ClientRequest.of(ILINK_BASE + path, HttpMethod.GET);
        request.setConnectTimeout(10_000L);
        request.setReadTimeout(60_000L);
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
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        var v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static int intVal(Map<String, Object> map, String key, int defaultVal) {
        if (map == null || !map.containsKey(key)) {
            return defaultVal;
        }
        var v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // ==================== 内部 DTO ====================

    /**
     * 扫码会话状态机（可变，用于多线程共享）。
     *
     * <p>状态流转：pending → ready（二维码就绪）→ scanned → confirmed / expired</p>
     */
    @Data
    static class QrSession {
        /** 会话唯一 key */
        String key;
        /** wechat / wecom */
        String platform;
        /** pending / ready / scanned / confirmed / expired / error */
        String status;
        /** iLink 返回的 qrcode 字符串，用于轮询 get_qrcode_status */
        String qrKey;
        /** 二维码图片 URL（iLink 返回的 qrcode_img_content，前端 img src 直接使用） */
        String qrImageUrl;
        /** 扫码确认后回填 */
        String botId;
        String botToken;
        String errorMsg;
        /** 会话超时时间戳（毫秒） */
        long expireAt;
    }

    /**
     * 生成二维码响应（不可变，用 record）。
     *
     * @param key      轮询 key（前端传给 /qrcode/status?key=）
     * @param qrcodeUrl 二维码图片 URL（async 模式即时返回空，前端轮询到 status=ready 后从 QrStatus 取）
     */
    public record QrResult(String key,
                           @JsonProperty("qrcodeUrl") String qrcodeUrl) {
    }

    /**
     * 轮询状态响应（不可变，用 record）。
     *
     * @param status    会话状态：pending / ready / scanned / confirmed / expired / error
     * @param qrcodeUrl ready 时附带，前端 img src 直接使用；其他状态为 null
     * @param botId     绑定成功后回填
     * @param botToken  绑定成功后回填
     */
    public record QrStatus(String status,
                           String qrcodeUrl,
                           String botId,
                           String botToken) {
    }
}
