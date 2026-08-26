package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.bot.QrcodeListener;
import com.chua.common.support.lang.json.Json;
import com.chua.ilink.support.bot.ILinkBotClient;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.mapper.SysAiBotBindingMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 iLink Bot 绑定服务。
 *
 * <p>管理多个 Bot 绑定记录的增删改查与在线同步。
 * 每条绑定对应一个独立的微信 Bot 实例。</p>
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

    /** 扫码会话内存表（key -> 会话状态）。生产环境可替换为 Redis。 */
    private final Map<String, QrSession> qrSessions = new ConcurrentHashMap<>();

    /**
     * 获取全部绑定列表。
     *
     * @return 绑定列表，按创建时间降序
     */
    public List<SysAiBotBinding> listAll() {
        return botBindingMapper.selectList(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .orderByDesc(SysAiBotBinding::getCreateTime));
    }

    /**
     * 按 ID 获取绑定。
     *
     * @param id 绑定 ID
     * @return 绑定信息或 null
     */
    public SysAiBotBinding getById(Long id) {
        return botBindingMapper.selectById(id);
    }

    /**
     * 按 botId 查找绑定。
     *
     * @param botId iLink 分配的唯一标识
     * @return 绑定信息或 null
     */
    public SysAiBotBinding findByBotId(String botId) {
        return botBindingMapper.selectOne(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .eq(SysAiBotBinding::getBotId, botId));
    }

    /**
     * 创建或更新绑定（按 botId 去重）。
     *
     * @param binding 绑定实体
     * @return 数据库 ID
     */
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

    /**
     * 更新在线状态。
     *
     * @param botId  Bot 唯一标识
     * @param status 新状态（online / offline / expired）
     */
    public void updateStatus(String botId, String status) {
        SysAiBotBinding binding = findByBotId(botId);
        if (binding != null) {
            binding.setStatus(status);
            binding.setLastActiveTime(LocalDateTime.now());
            binding.setUpdateTime(LocalDateTime.now());
            botBindingMapper.updateById(binding);
        }
    }

    /**
     * 删除绑定。
     *
     * @param id 数据库 ID
     */
    public void delete(Long id) {
        botBindingMapper.deleteById(id);
    }

    // ==================== iLink 扫码登录 ====================

    /**
     * 生成 iLink 二维码会话。
     *
     * <p>异步发起 {@link ILinkBotClient#loginWithQR()}，
     * 通过 {@link QrcodeListener} 回调捕获二维码 URL，
     * 登录成功时自动将凭证写入 {@code sys_ai_bot_binding} 表。</p>
     *
     * @param platform 平台标识（wechat / wecom），仅 wechat 支持扫码
     * @return 二维码会话 key 与 URL（可能为空，需轮询 status 确认）
     */
    public QrResult generateQrCode(String platform) {
        String key = UUID.randomUUID().toString();
        QrSession session = new QrSession();
        session.key = key;
        session.platform = platform;
        session.status = "pending";
        session.expireAt = System.currentTimeMillis() + 180_000L; // 3 分钟超时

        qrSessions.put(key, session);

        ILinkBotClient client = new ILinkBotClient();
        client.qrcodeListener(new QrcodeListener() {
            @Override
            public void newQrcode(String url, String k) {
                session.qrUrl = url;
                session.scanKey = k;
                log.info("[Bot] 二维码已生成 key={} url={}", key, url);
            }

            @Override
            public void scanned() {
                session.status = "scanned";
                log.info("[Bot] 二维码已扫描 key={}", key);
            }

            @Override
            public void confirmed(String token, String botId, String userId) {
                session.status = "confirmed";
                session.botId = botId;
                session.botToken = token;
                log.info("[Bot] 扫码登录成功 key={} botId={}", key, botId);
                // 自动持久化绑定记录
                SysAiBotBinding binding = new SysAiBotBinding();
                binding.setBotId(botId);
                binding.setBotToken(token);
                binding.setPlatform("wechat");
                binding.setNickname(userId); // userId 作为初始昵称占位，用户可在 UI 中修改
                binding.setGatewayPort(null);
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
            }

            @Override
            public void expired() {
                session.status = "expired";
                log.warn("[Bot] 二维码已过期 key={}", key);
            }

            @Override
            public void error(String reason, Throwable e) {
                session.status = "error";
                session.errorMsg = reason;
                log.error("[Bot] 扫码异常 key={} reason={}", key, reason, e);
            }
        });

        // 异步启动 iLink 登录流程（最长阻塞 180s）
        CompletableFuture.runAsync(() -> {
            try {
                client.loginWithQR();
            } catch (Exception e) {
                log.error("[Bot] loginWithQR 异常 key={}", key, e);
                session.status = "error";
                session.errorMsg = e.getMessage();
            }
        });

        // 等待二维码 URL 到位（最多 5 秒）
        long start = System.currentTimeMillis();
        while (session.qrUrl == null && System.currentTimeMillis() - start < 5_000) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
        }
        return new QrResult(key, session.qrUrl != null ? session.qrUrl : "");
    }

    /**
     * 查询二维码会话状态，供前端轮询。
     *
     * @param key 会话 key
     * @return 状态（pending / scanned / confirmed / expired / error）及凭证
     */
    public QrStatus checkQrCodeStatus(String key) {
        QrSession session = qrSessions.get(key);
        if (session == null) {
            throw new IllegalArgumentException("key 无效或已过期");
        }
        // 懒清理过期 session
        if (System.currentTimeMillis() > session.expireAt && !"confirmed".equals(session.status)) {
            session.status = "expired";
        }
        if ("confirmed".equals(session.status) && session.botId != null) {
            return new QrStatus("confirmed", session.botId, session.botToken);
        }
        return new QrStatus(session.status, null, null);
    }

    // ==================== 内部 DTO ====================

    /** 扫码会话状态机 */
    @Data
    static class QrSession {
        String key;
        String platform;
        String status;         // pending / scanned / confirmed / expired / error
        String qrUrl;
        String scanKey;        // iLink SDK 内部的 qr key
        String botId;
        String botToken;
        String errorMsg;
        long expireAt;
    }

    /** 生成二维码响应 */
    @Data
    public static class QrResult {
        /** 轮询 key */
        private final String key;
        /** 二维码图片 URL（iLink 提供，可直接 img src） */
        private final String qrcodeUrl;
    }

    /** 轮询状态响应 */
    @Data
    public static class QrStatus {
        /** pending / scanned / confirmed / expired / error */
        private final String status;
        /** 绑定成功后回填的 botId */
        private final String botId;
        /** 绑定成功后回填的 token */
        private final String botToken;
    }
}
