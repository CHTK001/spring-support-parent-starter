package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.bot.BotClient;
import com.chua.common.support.ai.bot.QrcodeListener;
import com.chua.feishu.support.bot.FeishuBotClient;
import com.chua.ilink.support.bot.ILinkBotClient;
import com.chua.whatsapp.support.bot.WhatsAppBotClient;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.manage.store.BotUserSummary;
import com.chua.starter.ai.support.mapper.SysAiBotBindingMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 微信 i链接 机器人 绑定服务。
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

    /**
     * 机器人 绑定 映射器
    */
    private final SysAiBotBindingMapper botBindingMapper;

    /**
     * 机器人 聊天引擎（微信消息轮询 + AI 回复）
    */
    private final AiBotChatService aiBotChatService;

    /**
     * 扫码会话内存表（Key -> 会话状态）。
     *
     * <p>使用 ConcurrentHashMap 避免高并发下 session 互相覆盖；
     * 生产环境替换为 Redis，Key 带前缀避免冲突。</p>
     */
    private final ConcurrentHashMap<String, QrSession> qrSessions = new ConcurrentHashMap<>();

    // ==================== 绑定 CRUD ====================

    /**
     * 获取全部绑定列表（按创建时间降序）。
     * @return 列表全部的结果
     */
    public List<SysAiBotBinding> listAll() {
        return botBindingMapper.selectList(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .orderByDesc(SysAiBotBinding::getCreateTime));
    }

    /**
     * 管理员视角查看指定用户绑定的 机器人 列表。
     *
     * @param userId 用户 标识（空 表示未绑定用户的全部记录）
     * @return 绑定列表（按创建时间降序）
     */
    public List<SysAiBotBinding> listByUser(Long userId) {
        return botBindingMapper.selectList(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .eq(userId != null, SysAiBotBinding::getUserId, userId)
                        .isNull(userId == null, SysAiBotBinding::getUserId)
                        .orderByDesc(SysAiBotBinding::getCreateTime));
    }

    /**
     * 按归属标识过滤绑定列表（用于越权过滤）。
     *
     * <p>登录用户归属为 {@code u:<userId>}，匿名用户归属为 {@code fp:<浏览器指纹>}。
     * 传入 {@code null} / 空串时只返回未归属（历史遗留）记录。</p>
     *
     * @param ownerKey 归属标识（可为空）
     * @return 绑定列表（按创建时间降序）
     */
    public List<SysAiBotBinding> listByOwnerKey(String ownerKey) {
        boolean blank = ownerKey == null || ownerKey.isBlank();
        return botBindingMapper.selectList(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .eq(!blank, SysAiBotBinding::getOwnerKey, ownerKey)
                        .isNull(blank, SysAiBotBinding::getOwnerKey)
                        .orderByDesc(SysAiBotBinding::getCreateTime));
    }

    /**
     * 管理员视角的用户聚合列表：按 用户标识 分组统计每个用户绑定的 机器人 数与在线数。
     *
     * @return 用户摘要列表（含未分配用户组的记录）
     */
    public List<BotUserSummary> listUserSummaries() {
        List<SysAiBotBinding> all = botBindingMapper.selectList(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .orderByDesc(SysAiBotBinding::getCreateTime));
        Map<Long, List<SysAiBotBinding>> byUser = all.stream()
                .filter(b -> b.getUserId() != null)
                .collect(Collectors.groupingBy(SysAiBotBinding::getUserId));

        List<BotUserSummary> result = new ArrayList<>();
        for (Map.Entry<Long, List<SysAiBotBinding>> e : byUser.entrySet()) {
            BotUserSummary s = summarize(e.getKey(), e.getValue());
            result.add(s);
        }
 // 未分配用户（用户标识 为 空）的记录归入同一组
        List<SysAiBotBinding> unassigned = all.stream()
                .filter(b -> b.getUserId() == null)
                .collect(Collectors.toList());
        if (!unassigned.isEmpty()) {
            result.add(summarize(null, unassigned));
        }
        return result;
    }

    /**
     * 将同属一个用户的绑定记录聚合成摘要。
     *
     * @param userId 用户 标识（可空，表示未分配）
     * @param bots   该用户的绑定记录
     * @return 用户摘要
     */
    private BotUserSummary summarize(Long userId, List<SysAiBotBinding> bots) {
        BotUserSummary summary = new BotUserSummary();
        summary.setOwner(userId != null ? String.valueOf(userId) : "unassigned");
        long online = bots.stream().filter(b -> "online".equals(b.getStatus())).count();
        summary.setBotCount(bots.size());
        summary.setOnlineCount(online);
        summary.setOfflineCount(bots.size() - online);
        summary.setNickname(bots.stream()
                .map(SysAiBotBinding::getNickname)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(userId != null ? String.valueOf(userId) : "未分配"));
        summary.setLastActiveTime(bots.stream()
                .map(SysAiBotBinding::getLastActiveTime)
                .filter(Objects::nonNull)
                .map(t -> t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .max(Long::compareTo)
                .orElse(null));
        return summary;
    }

    /**
     * 按 标识 查询 机器人 绑定记录。
     *
     * @param id 绑定记录 标识，不能为 空
     * @return 绑定记录，不存在时返回 空
     */
    public SysAiBotBinding getById(Long id) {
        return botBindingMapper.selectById(id);
    }

    /**
     * 按 机器人 唯一标识查询绑定记录。
     *
     * @param botId 机器人 唯一标识，不能为 空 或空字符串
     * @return 绑定记录，不存在时返回 空
     */
    public SysAiBotBinding findByBotId(String botId) {
        return botBindingMapper.selectOne(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .eq(SysAiBotBinding::getBotId, botId));
    }

    /**
     * 创建或更新绑定记录（按 机器人标识 去重）。
     *
     * <p>若 botId 已存在则更新，否则插入新记录。
     * MyBatis Plus NOT_空 更新策略：空 字段不会覆盖数据库值（令牌 留空即保留原值）。</p>
     * @param binding binding
     * @return 保存or更新的结果
     */
    public Long saveOrUpdate(SysAiBotBinding binding) {
        var existing = findByBotId(binding.getBotId());
        if (existing != null) {
            binding.setId(existing.getId());
            binding.setUpdateTime(LocalDateTime.now());
            botBindingMapper.updateById(binding);
        } else {
            binding.setCreateTime(LocalDateTime.now());
            binding.setUpdateTime(LocalDateTime.now());
            botBindingMapper.insert(binding);
        }
 // 微信 机器人 且在线且有凭证时，启动/刷新其消息轮询（AI 聊天）
        if ("wechat".equals(binding.getPlatform())
                && "online".equals(binding.getStatus())
                && binding.getBotToken() != null && !binding.getBotToken().isBlank()) {
            aiBotChatService.startBot(binding);
        }
        return binding.getId() != null ? binding.getId() : existing.getId();
    }

    /**
     * 更新 机器人 在线状态（由内部同步任务调用，不对接前端）。
     * @param botId 机器人标识
     * @param status 状态
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
        // 状态变化时启停消息轮询
        if ("online".equals(status)) {
            aiBotChatService.startBot(binding);
        } else {
            aiBotChatService.stopBot(botId);
        }
    }

    /**
     * 删除。
     * @param id 标识
     */
    public void delete(Long id) {
        var binding = getById(id);
        botBindingMapper.deleteById(id);
        if (binding != null && binding.getBotId() != null) {
            aiBotChatService.stopBot(binding.getBotId());
        }
    }

    /**
     * 编辑绑定的展示名称与对话模型（绑定后「编辑」入口）。
     *
     * <p>仅更新 nickname / model 两列，不触碰凭证、状态，也不重启消息轮询；
     * model 传 {@code null} 表示不修改，传空串表示跟随系统默认模型。</p>
     *
     * @param id       绑定记录标识
     * @param nickname 展示名称（{@code null} 表示不修改）
     * @param model    对话模型（{@code null} 表示不修改，空串为默认）
     * @return 更新后的绑定记录，不存在时返回 {@code null}
     */
    public SysAiBotBinding updateSettings(Long id, String nickname, String model) {
        SysAiBotBinding binding = getById(id);
        if (binding == null) {
            return null;
        }
        if (nickname != null) {
            binding.setNickname(nickname);
        }
        if (model != null) {
            binding.setModel(model);
        }
        binding.setUpdateTime(LocalDateTime.now());
        botBindingMapper.updateById(binding);
        return binding;
    }

    /**
     * 通用编辑持久化：对已加载并就地修改过的绑定记录执行按 id 更新。
     *
     * <p>仅做 updateById，不按 botId upsert、不重启消息轮询，供连接器「编辑」
     * 一次性保存名称 / 模型 / @过滤 / 随机回复等可编辑字段。</p>
     *
     * @param binding 已含 id 的绑定记录
     */
    public void updateBinding(SysAiBotBinding binding) {
        if (binding == null || binding.getId() == null) {
            return;
        }
        binding.setUpdateTime(LocalDateTime.now());
        botBindingMapper.updateById(binding);
    }

 // ==================== 微信 i链接 扫码登录 ====================

    /**
     * 生成微信 i链接 扫码会话。
     *
     * <p>通过 {@link BotClient#auto(String)} SPI 构建 {@link ILinkBotClient}，
     * 注册 {@link QrcodeListener} 回调，后台线程执行扫码登录流程。<br>
     * 二维码生成与扫码确认均由 SDK 内部长轮询驱动，回调实时更新会话状态。</p>
     *
     * <p>企业微信（wecom）iLink 协议不支持扫码，直接返回空 key，
     * 前端识别后提示改用手动导入。</p>
     *
     * @param platform wechat / wecom
     * @return 会话 Key（前端传给 /qrcode/状态）+ qrcodeurl（轮询 状态=就绪 后从 qr状态 取）
     */
    public QrResult generateQrCode(String platform) {
        return generateQrCode(platform, null, null);
    }

    /**
     * 生成微信 i链接 扫码会话（不带归属标识，兼容旧调用）。
     *
     * @param platform wechat / wecom
     * @param oldBotId 重新扫码场景下需合并的旧 机器人 唯一标识（可为空）
     * @return 会话 Key + qrcodeurl
     */
    public QrResult generateQrCode(String platform, String oldBotId) {
        return generateQrCode(platform, oldBotId, null);
    }

    /**
     * 生成微信 i链接 扫码会话（支持"重新扫码合并到旧绑定"）。
     *
     * <p>当旧绑定过期需重新扫码时，调用方可传入 {@code oldBotId}；
     * 扫码确认后，系统会把新 机器人标识/机器人令牌 合并写入旧绑定记录，并把旧 机器人标识
     * 下的历史聊天消息迁移到新 机器人标识，保证前端会话与聊天记录不分裂。</p>
     *
     * @param platform wechat / wecom
     * @param oldBotId 重新扫码场景下需合并的旧 机器人 唯一标识（可为空，表示全新绑定）
     * @return 会话 Key + qrcodeurl
     * @param ownerKey ownerKey
     */
    public QrResult generateQrCode(String platform, String oldBotId, String ownerKey) {
 // 企业微信不支持 i链接 扫码，直接返回空 Key
        if ("wecom".equals(platform)) {
            log.info("[Bot] wecom 扫码被拒绝，使用手动导入");
            /**
             * qr结果。
             * @param UUID.randomUUID( UUID.随机uuid(
             * @return qr结果的结果
             */
            return new QrResult(UUID.randomUUID().toString(), "");
        }

        var key = UUID.randomUUID().toString();
        var session = new QrSession();
        session.key = key;
        session.platform = "wechat";
        session.status = "pending";
        session.oldBotId = oldBotId != null && !oldBotId.isBlank() ? oldBotId.trim() : null;
        session.ownerKey = ownerKey;
        session.expireAt = System.currentTimeMillis() + 180_000L; // 3 分钟超时
        qrSessions.put(key, session);

        try {
 // 通过 SPI 构建 i链接 机器人客户端
            BotClient auto = BotClient.auto("ilink");
            if (!(auto instanceof ILinkBotClient ilink)) {
                session.status = "error";
                session.errorMsg = "iLink SPI 未返回 ILinkBotClient";
                /**
                 * qr结果。
                 * @param key Key
                 * @param "" ""
                 * @return qr结果的结果
                 */
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
 // 校验凭证非空，避免写入 机器人标识 为空的脏数据
                    if (botId == null || botId.isBlank()) {
                        log.warn("[Bot] 扫码确认但缺少 botId，跳过绑定 key={}", key);
                    } else {
                        /**
                         * 保存binding从扫描。
                         * @param key Key
                         * @param session 会话
                         * @param userId 用户标识
                         */
                        saveBindingFromScan(key, session, userId);
                    }
 // 保留 会话 让前端轮询能读到 confirmed 状态后再关闭；
                    // 会话到期后由懒清理机制回收
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

 // 同步等待二维码 URL 生成（i链接 异步返回，最长等 8s），确保 POST /qrcode 直接返回二维码
            long waitStart = System.currentTimeMillis();
            while (session.qrImageUrl == null && "pending".equals(session.status)
                    && System.currentTimeMillis() - waitStart < 8_000L) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            session.status = "error";
            session.errorMsg = "iLink SPI 加载失败: " + e.getMessage();
            log.error("[Bot] SPI 构建失败 key={}", key, e);
        }

        // 直接返回二维码 URL（若同步等待期间已就绪）；前端无需再额外轮询拿 URL
        /**
         * qr结果。
         * @param key Key
         * @param "" ""
         * @return qr结果的结果
         */
        return new QrResult(key, session.qrImageUrl != null ? session.qrImageUrl : "");
    }

    /**
     * 轮询扫码会话状态（前端每 3 秒调用一次）。
     *
     * <p>会话状态由后台 {@link ILinkBotClient} 扫码流程的
     * {@link QrcodeListener} 回调实时更新，此处仅读取缓存，不做网络请求。</p>
     *
     * <p>会话 Key 虽为随机串，但本出口在鉴权白名单内，故按归属收口：会话已归属时只有同一归属
     * 可以查询，其余视同会话不存在。凭证不随状态下发，落库由 {@link #saveBindingFromScan} 完成。</p>
     *
     * @param key            会话 Key
     * @param callerOwnerKey 当前调用方归属标识，可为空
     * @return 状态 + 机器人标识（confirmed 时）
     */
    public QrStatus checkQrCodeStatus(String key, String callerOwnerKey) {
        var session = qrSessions.get(key);
 // 会话不存在（已 confirmed 移除 / 已过期 / Key 无效 / 非本人会话）：返回 expired，避免前端轮询报错
        if (session == null || !sameOwner(session.ownerKey, callerOwnerKey)) {
            return new QrStatus("expired", null, null, null);
        }
 // 懒清理过期 会话
        if (System.currentTimeMillis() > session.expireAt && !"confirmed".equals(session.status)) {
            session.status = "expired";
            qrSessions.remove(key);
        }
        if ("confirmed".equals(session.status) && session.botId != null) {
            return new QrStatus("confirmed", session.qrImageUrl, session.botId, session.expireAt);
        }
        /**
         * qr状态。
         * @param "pending" "pending"
         * @param session.qrImageUrl 会话.qr镜像url
         * @param null 空
         * @param session.expireAt 会话.expireat
         * @return qr状态的结果
         */
        return new QrStatus(session.status != null ? session.status : "pending", session.qrImageUrl, null, session.expireAt);
    }

    /**
     * 会话归属与调用方归属是否一致。
     *
     * @param sessionOwnerKey 会话归属标识，空表示未归属（桌面免登录场景）
     * @param callerOwnerKey  调用方归属标识，可为空
     * @return 一致或会话未归属时为 {@code true}
     */
    private static boolean sameOwner(String sessionOwnerKey, String callerOwnerKey) {
        if (sessionOwnerKey == null || sessionOwnerKey.isBlank()) {
            return true;
        }
        return sessionOwnerKey.equals(callerOwnerKey);
    }

    /**
     * 扫码成功后自动持久化绑定记录。
     *
     * <p>将 iLink 返回的 botId、botToken 写入 sys_ai_bot_binding，
     * 绑定来源标记为 qrcode_扫描，状态设为 online。</p>
     *
     * <p><b>重新扫码自动合并并保留历史：</b>微信用户身份不变（nickname=userId），
     * 重新扫码后 i链接 可能分配新的 机器人标识。此时自动完成三件事：
     * <ol>
     *   <li>把同微信号（nickname）所有旧 botId 下的聊天消息迁移到新 botId</li>
     *   <li>更新其中一条旧绑定记录为新的 botId/botToken（记录 id 不变，前端会话不分裂）</li>
     *   <li>删除同微信用户的其他旧绑定记录，避免重复行</li>
     * </ol></p>
     * @param key Key
     * @param session 会话
     * @param userId 用户标识
     */
    private void saveBindingFromScan(String key, QrSession session, String userId) {
        try {
            String newBotId = session.botId;
            if (newBotId == null || newBotId.isBlank()) {
                log.warn("[Bot] 扫码确认缺少 botId，跳过绑定 key={}", key);
                return;
            }
            String platform = "wechat";
 // 请求方显式传入的旧 机器人标识（机器人im 重新扫码）也纳入合并范围
            String explicitOldBotId = session.oldBotId;

 // 1. 找出所有应合并的旧绑定：同一微信用户（nickname=用户id）的本平台绑定，
 // 外加显式指定的旧 机器人标识（即便 nickname 对不上也合并）
            List<SysAiBotBinding> candidates = new ArrayList<>();
            if (userId != null && !userId.isBlank()) {
                candidates.addAll(botBindingMapper.selectList(
                        Wrappers.<SysAiBotBinding>lambdaQuery()
                                .eq(SysAiBotBinding::getPlatform, platform)
                                .eq(SysAiBotBinding::getNickname, userId)));
            }
            if (explicitOldBotId != null && !explicitOldBotId.isBlank()) {
                var byExplicit = findByBotId(explicitOldBotId);
                if (byExplicit != null && platform.equals(byExplicit.getPlatform())
                        && candidates.stream().noneMatch(c -> c.getId().equals(byExplicit.getId()))) {
                    candidates.add(byExplicit);
                }
            }
 // 移除本身就是新 机器人标识 的行（无需处理），去重
            candidates = candidates.stream()
                    .filter(b -> !newBotId.equals(b.getBotId()))
                    .distinct()
                    .collect(Collectors.toList());

            if (!candidates.isEmpty()) {
 // 2. 迁移所有旧 机器人标识 的历史消息到新 机器人标识
                for (SysAiBotBinding old : candidates) {
                    if (old.getBotId() != null && !old.getBotId().isBlank()) {
                        aiBotChatService.migrateMessages(old.getBotId(), newBotId);
                    }
                }
 // 3. 保留第一行作为合并目标：更新为新 机器人标识/机器人令牌/online；其余删除
                SysAiBotBinding target = candidates.getFirst();
                target.setBotId(newBotId);
                target.setBotToken(session.botToken);
                target.setPlatform(platform);
                target.setStatus("online");
                target.setBindSource("qrcode_scan");
                // 领取归属：旧记录未归属时，用本次请求的归属标识补上
                if ((target.getOwnerKey() == null || target.getOwnerKey().isBlank())
                        && session.ownerKey != null && !session.ownerKey.isBlank()) {
                    target.setOwnerKey(session.ownerKey);
                }
                target.setUpdateTime(LocalDateTime.now());
                if (userId != null && !userId.isBlank() && !userId.equals(target.getNickname())) {
                    target.setNickname(userId);
                }
                botBindingMapper.updateById(target);
                for (int i = 1; i < candidates.size(); i++) {
                    SysAiBotBinding dup = candidates.get(i);
                    if (!dup.getId().equals(target.getId())) {
                        botBindingMapper.deleteById(dup.getId());
                        log.info("[Bot] 清理重复旧绑定 id={} {}", dup.getId(), dup.getBotId());
                    }
                }
 // 4. 启动新 机器人 的消息轮询
                aiBotChatService.startBot(target);
                log.info("[Bot] 重新扫码已合并绑定 id={} {} → {}", target.getId(),
                        target.getBotId(), newBotId);
                return;
            }

 // 5. 无旧绑定可合并：全新绑定（按 机器人标识 去重写入）
            var binding = new SysAiBotBinding();
            binding.setBotId(newBotId);
            binding.setBotToken(session.botToken);
            binding.setPlatform(platform);
            binding.setNickname("微信助理");
            binding.setStatus("online");
            binding.setBindSource("qrcode_scan");
            binding.setOwnerKey(session.ownerKey);
            binding.setCreateTime(LocalDateTime.now());
            binding.setUpdateTime(LocalDateTime.now());
            /**
             * 保存或更新。
             * @param binding binding
             */
            saveOrUpdate(binding);
            log.info("[Bot] 绑定已写入 DB id={}", binding.getId());
        } catch (Exception e) {
            log.error("[Bot] 写入绑定失败 key={} botId={}", key, session.botId, e);
        }
    }

    // ==================== 飞书（凭据配置型） ====================

    /**
     * 绑定飞书 机器人（凭据配置型）。
     *
     * <p>通过 SPI 构建 {@link FeishuBotClient}，用 appId/appSecret 初始化并 start() 校验，
     * 成功后写入 sys_AI_机器人_binding（platform=feishu，bind源=manual_导入）。</p>
     *
     * @param appId     飞书开放平台应用 标识
     * @param appSecret 飞书开放平台应用密钥
     * @return 数据库 标识
     */
    public Long bindFeishu(String appId, String appSecret) {
        return bindFeishu(appId, appSecret, null, null);
    }

    public Long bindFeishu(String appId, String appSecret, String ownerKey) {
        return bindFeishu(appId, appSecret, null, ownerKey);
    }

    /**
     * 绑定飞书 机器人（凭据配置型，带归属标识与初始模型）。
     *
     * @param appId     飞书开放平台应用 标识
     * @param appSecret 飞书开放平台应用密钥
     * @param model     初始对话模型（可空，空=跟随默认）
     * @param ownerKey  归属标识（可空，空=公共/未归属）
     * @return 数据库 标识
     */
    public Long bindFeishu(String appId, String appSecret, String model, String ownerKey) {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("飞书 appId 与 appSecret 必填");
        }
        // 通过 SPI 构建并校验
        try {
            BotClient auto = BotClient.auto("feishu");
            if (!(auto instanceof FeishuBotClient feishu)) {
                throw new IllegalStateException("feishu SPI 未返回 FeishuBotClient");
            }
            // 仅轻量校验凭据（获取 tenant_access_token），不建立长连接；
            // 否则校验连接与 startBot 的托管连接并存，事件会被无监听器的校验连接接走
            FeishuBotClient.fetchTenantAccessToken(appId, appSecret);
            log.info("[Bot] 飞书凭据校验成功 appId={}", appId);
        } catch (Exception e) {
            log.error("[Bot] 飞书绑定失败 appId={}", appId, e);
            /**
             * IllegalArgumentException。
             * @param e.getMessage( e.获取消息(
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("飞书客户端初始化失败: " + e.getMessage());
        }

        var binding = new SysAiBotBinding();
        binding.setBotId(appId);
        binding.setBotToken(appSecret);
        binding.setPlatform("feishu");
        binding.setNickname("飞书 Bot");
        binding.setStatus("online");
        binding.setBindSource("manual_import");
        if (ownerKey != null && !ownerKey.isBlank()) {
            binding.setOwnerKey(ownerKey);
        }
        binding.setCreateTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        if (model != null && !model.isBlank()) {
            binding.setModel(model.trim());
        }
        return saveOrUpdate(binding);
    }

    // ==================== QQ（凭据配置型） ====================

    /**
     * 绑定 QQ 机器人（凭据配置型）。
     *
     * <p>校验 App ID / App Secret 非空后写入 sys_ai_bot_binding（platform=qq，
     * bind_source=manual_import）。真实网关连接由 {@code AiBotChatService.startBot}
     * 统一创建（WebSocket 长连接，内部负责换取 access_token、心跳与重连）。</p>
     *
     * @param appId     QQ 开放平台机器人 App ID
     * @param appSecret QQ 开放平台机器人 App Secret
     * @return 数据库标识
     */
    public Long bindQq(String appId, String appSecret) {
        return bindQq(appId, appSecret, null, null);
    }

    public Long bindQq(String appId, String appSecret, String ownerKey) {
        return bindQq(appId, appSecret, null, ownerKey);
    }

    /**
     * 绑定 QQ 机器人（凭据配置型，带归属标识与初始模型）。
     *
     * @param appId     QQ 机器人 App ID
     * @param appSecret QQ 机器人 App Secret
     * @param model     初始对话模型（可空，空=跟随默认）
     * @param ownerKey  归属标识（可空，空=公共/未归属）
     * @return 数据库标识
     */
    public Long bindQq(String appId, String appSecret, String model, String ownerKey) {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("QQ App ID 与 App Secret 必填");
        }

        var binding = new SysAiBotBinding();
        binding.setBotId(appId);
        binding.setBotToken(appSecret);
        binding.setPlatform("qq");
        binding.setNickname("QQ Bot");
        binding.setStatus("online");
        binding.setBindSource("manual_import");
        if (ownerKey != null && !ownerKey.isBlank()) {
            binding.setOwnerKey(ownerKey);
        }
        binding.setCreateTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        if (model != null && !model.isBlank()) {
            binding.setModel(model.trim());
        }
        // 凭据型不在此触发 startBot（saveOrUpdate 仅自动启动 wechat），由 /bind 统一托管
        return saveOrUpdate(binding);
    }

    // ==================== WhatsApp（Cloud API 凭据配置型） ====================

    /**
     * 绑定 WhatsApp Cloud API 机器人（凭据配置型）。
     *
     * @param phoneNumberId WhatsApp Business phone_number_id
     * @param accessToken   Meta Graph access_token
     * @param appSecret     Meta App Secret（签名校验，可空）
     * @param verifyToken   Webhook 校验令牌（可空）
     * @param model         初始对话模型（可空，空=跟随默认）
     * @param ownerKey      归属标识（可空）
     * @return 数据库标识
     */
    public Long bindWhatsApp(String phoneNumberId, String accessToken, String appSecret,
                             String verifyToken, String model, String ownerKey) {
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalArgumentException("WhatsApp phone_number_id 必填");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("WhatsApp access_token 必填");
        }

        // 轻量校验：只调用 GET /{phone_number_id}，不建立任何长连接
        try {
            WhatsAppBotClient client = new WhatsAppBotClient();
            client.configure(phoneNumberId, accessToken, appSecret, verifyToken);
            client.validate();
            log.info("[Bot] WhatsApp 凭据校验成功 phoneNumberId={}", phoneNumberId);
        } catch (IllegalArgumentException e) {
            if (e.getCause() == null) {
                // HTTP 层已返回响应（如 401/403），说明凭据确实无效，拒绝绑定
                log.error("[Bot] WhatsApp 绑定失败 phoneNumberId={}", phoneNumberId, e);
                throw new IllegalArgumentException("WhatsApp 凭据校验失败: " + e.getMessage());
            }
            // 网络不可达（跨GFW/无出口代理等）：保存绑定，交由 Meta Webhook 握手做最终校验
            log.warn("[Bot] WhatsApp 凭据校验因网络不可达跳过 phoneNumberId={}：{}",
                    phoneNumberId, e.getMessage());
        }

        var binding = new SysAiBotBinding();
        binding.setBotId(phoneNumberId);
        binding.setBotToken(accessToken);
        binding.setAppSecret(appSecret);
        binding.setVerifyToken(verifyToken);
        binding.setPlatform("whatsapp");
        binding.setNickname("WhatsApp Bot");
        binding.setStatus("online");
        binding.setBindSource("manual_import");
        if (ownerKey != null && !ownerKey.isBlank()) {
            binding.setOwnerKey(ownerKey);
        }
        binding.setCreateTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        if (model != null && !model.isBlank()) {
            binding.setModel(model.trim());
        }
        return saveOrUpdate(binding);
    }

    // ==================== 内部 DTO ====================

    /**
     * 扫码会话状态机（可变，用于多线程共享）。
     *
     * <p>状态流转：pending → ready（二维码就绪）→ scanned → confirmed / expired / error</p>
     * @author CH
     * @since 4.0.0
     */
    @Data
    static class QrSession {
        /**
         * 会话唯一 Key
        */
        String key;
        /**
         * 平台类型
        */
        String platform;
        /**
         * pending / 就绪 / scanned / confirmed / expired / 错误
        */
        String status;
        /**
         * i链接 返回的 qrcode Key
        */
        String qrKey;
        /**
         * 二维码图片 URL（i链接 返回的 qrcode_img_内容，前端 img src 直接使用）
        */
        String qrImageUrl;
        /**
         * 扫码确认后回填
        */
        String botId;
        String botToken; // 机器人令牌
        String errorMsg; // 错误msg
        /**
         * 重新扫码场景：需要合并的旧 机器人 唯一标识（原绑定记录）
        */
        String oldBotId;
        /**
         * 发起扫码时的归属标识（登录 u:<userId> / 匿名 fp:<fingerprint>），用于写入绑定记录
        */
        String ownerKey;
        /**
         * 会话超时时间戳（毫秒）
        */
        long expireAt;
    }

    /**
     * 生成二维码响应（不可变，用 record）。
     *
     * @param key       轮询 Key（前端传给 /qrcode/状态?Key=）
     * @param qrcodeUrl 二维码图片 URL（异步 模式即时返回空，前端轮询到 状态=就绪 后从 qr状态 取）
     */
    public record QrResult(String key,
                           @JsonProperty("qrcodeUrl") String qrcodeUrl) {
    }

    /**
     * 轮询状态响应（不可变，用 record）。
     *
     * @param status    会话状态：pending / 就绪 / scanned / confirmed / expired / 错误
     * @param qrcodeUrl 就绪 时附带，前端 img src 直接使用；其他状态为 空
     * @param botId     绑定成功后回填
     * @param expireAt  二维码过期时间戳（毫秒），前端据此展示倒计时并到期自动刷新
     */
    public record QrStatus(String status,
                           String qrcodeUrl,
                           String botId,
                           @JsonProperty("expireAt") Long expireAt) {
    }
}
