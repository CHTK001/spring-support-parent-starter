package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.bot.BotClient;
import com.chua.common.support.ai.bot.QrcodeListener;
import com.chua.feishu.support.bot.FeishuBotClient;
import com.chua.ilink.support.bot.ILinkBotClient;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.mapper.SysAiBotBindingMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 iLink Bot 绑定服务。
 *
 * <p>管理多个 Bot 绑定记录的增删改查与扫码登录。</p>
 *
 * <p>平台绑定方式：
 * <ul>
 *   <li>微信（wechat / iLink）：扫码绑定，通过 {@link BotClient} SPI 构建 {@link ILinkBotClient}。</li>
 *   <li>飞书（feishu）：凭据配置（appId/appSecret），通过 SPI 构建 {@link FeishuBotClient} 校验后入库。</li>
 *   <li>企业微信（wecom）：暂不支持扫码，仅手动导入。</li>
 * </ul></p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBotBindingService {

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

    // ==================== 微信 iLink 扫码登录 ====================

    /**
     * 生成微信 iLink 扫码会话。
     *
     * <p>通过 {@link BotClient#auto(String)} SPI 构建 {@link ILinkBotClient}，
     * 注册 {@link QrcodeListener} 回调，后台线程执行扫码登录流程。<br>
     * 二维码生成与扫码确认均由 SDK 内部长轮询驱动，回调实时更新会话状态。</p>
     *
     * <p>企业微信（wecom）iLink 协议不支持扫码，直接返回空 key，
     * 前端识别后提示改用手动导入。</p>
     *
     * @param platform wechat / wecom
     * @return 会话 key（前端传给 /qrcode/status）+ qrcodeUrl（轮询 status=ready 后从 QrStatus 取）
     */
    public QrResult generateQrCode(String platform) {
        // 企业微信不支持 iLink 扫码，直接返回空 key
        if ("wecom".equals(platform)) {
            log.info("[Bot] wecom 扫码被拒绝，使用手动导入");
            return new QrResult(UUID.randomUUID().toString(), "");
        }

        var key = UUID.randomUUID().toString();
        var session = new QrSession();
        session.key = key;
        session.platform = "wechat";
        session.status = "pending";
        session.expireAt = System.currentTimeMillis() + 180_000L; // 3 分钟超时
        qrSessions.put(key, session);

        try {
            // 通过 SPI 构建 iLink BotClient
            BotClient auto = BotClient.auto("ilink");
            if (!(auto instanceof ILinkBotClient ilink)) {
                session.status = "error";
                session.errorMsg = "iLink SPI 未返回 ILinkBotClient";
                return new QrResult(key, "");
            }

            // 注册二维码生命周期回调，实时更新会话状态
            ilink.qrcodeListener(new QrcodeListener() {
                @Override
                public void newQrcode(String url, String k) {
                    session.qrKey = k;
                    session.qrImageUrl = url;
                    session.status = "ready";
                    log.info("[Bot] 二维码已生成 key={} url={}", key, url);
                }

                @Override
                public void scanned() {
                    session.status = "scanned";
                    log.info("[Bot] 已扫码 key={}", key);
                }

                @Override
                public void confirmed(String token, String botId, String userId) {
                    session.status = "confirmed";
                    session.botId = botId;
                    session.botToken = token;
                    log.info("[Bot] 扫码确认 key={} botId={}", key, botId);
                    saveBindingFromScan(key, session, userId);
                    qrSessions.remove(key);
                }

                @Override
                public void expired() {
                    session.status = "expired";
                    log.warn("[Bot] 二维码过期 key={}", key);
                }

                @Override
                public void error(String reason, Throwable e) {
                    session.status = "error";
                    session.errorMsg = reason;
                    log.error("[Bot] 扫码异常 key={} reason={}", key, reason, e);
                }
            });

            // 后台线程执行扫码登录（内部长轮询，直到 confirmed/expired）
            CompletableFuture.runAsync(() -> {
                try {
                    ilink.start();
                } catch (Exception e) {
                    session.status = "error";
                    session.errorMsg = e.getMessage();
                    log.error("[Bot] 扫码登录异常 key={}", key, e);
                }
            });
        } catch (Exception e) {
            session.status = "error";
            session.errorMsg = "iLink SPI 加载失败: " + e.getMessage();
            log.error("[Bot] SPI 构建失败 key={}", key, e);
        }

        // 立即返回 key，二维码 URL 通过轮询 status=ready 获取
        return new QrResult(key, "");
    }

    /**
     * 轮询扫码会话状态（前端每 3 秒调用一次）。
     *
     * <p>会话状态由后台 {@link ILinkBotClient} 扫码流程的
     * {@link QrcodeListener} 回调实时更新，此处仅读取缓存，不做网络请求。</p>
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
        }
        if ("confirmed".equals(session.status) && session.botId != null) {
            return new QrStatus("confirmed", session.qrImageUrl, session.botId, session.botToken);
        }
        return new QrStatus(session.status != null ? session.status : "pending", session.qrImageUrl, null, null);
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
            binding.setPlatform("wechat");
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

    // ==================== 飞书（凭据配置型） ====================

    /**
     * 绑定飞书 Bot（凭据配置型）。
     *
     * <p>通过 SPI 构建 {@link FeishuBotClient}，用 appId/appSecret 初始化并 start() 校验，
     * 成功后写入 sys_ai_bot_binding（platform=feishu，bindSource=manual_import）。</p>
     *
     * @param appId     飞书开放平台应用 ID
     * @param appSecret 飞书开放平台应用密钥
     * @return 数据库 ID
     */
    public Long bindFeishu(String appId, String appSecret) {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("飞书 appId 与 appSecret 必填");
        }
        // 通过 SPI 构建并校验
        try {
            BotClient auto = BotClient.auto("feishu");
            if (!(auto instanceof FeishuBotClient feishu)) {
                throw new IllegalStateException("feishu SPI 未返回 FeishuBotClient");
            }
            feishu.configure(appId, appSecret, null).start();
            log.info("[Bot] 飞书客户端初始化成功 appId={}", appId);
        } catch (Exception e) {
            log.error("[Bot] 飞书绑定失败 appId={}", appId, e);
            throw new IllegalArgumentException("飞书客户端初始化失败: " + e.getMessage());
        }

        var binding = new SysAiBotBinding();
        binding.setBotId(appId);
        binding.setBotToken(appSecret);
        binding.setPlatform("feishu");
        binding.setNickname("飞书 Bot");
        binding.setStatus("online");
        binding.setBindSource("manual_import");
        binding.setCreateTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        return saveOrUpdate(binding);
    }

    // ==================== 内部 DTO ====================

    /**
     * 扫码会话状态机（可变，用于多线程共享）。
     *
     * <p>状态流转：pending → ready（二维码就绪）→ scanned → confirmed / expired / error</p>
     */
    @Data
    static class QrSession {
        /** 会话唯一 key */
        String key;
        /** 平台类型 */
        String platform;
        /** pending / ready / scanned / confirmed / expired / error */
        String status;
        /** iLink 返回的 qrcode key */
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
     * @param key       轮询 key（前端传给 /qrcode/status?key=）
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
