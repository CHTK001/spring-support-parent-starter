package com.chua.starter.ai.support.stream;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.agent.AgentTrace;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiMessage;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.mapper.SysAiMessageMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.oauth.client.support.user.UserResume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 重启后自动继续：把「用户已发问、助手还没回完」的会话补跑完。
 *
 * <p><b>什么算未完成</b>：服务在流式过程中被杀掉时，助手消息还没来得及落库，
 * 表里最后一条消息因此是 {@code user} 角色。重启后这一轮不会自己恢复，
 * 用户看到的是「只有提问、没有回答」。本服务在启动完成时扫一遍最近的会话，
 * 命中这种形态就补发一轮 {@code 继续}，由正常链路把回复补齐。</p>
 *
 * <p><b>边界</b>：</p>
 * <ul>
 *   <li>只扫最近 {@value #MAX_AGE_HOURS} 小时有更新的会话，最多 {@value #SCAN_LIMIT} 个，
 *       避免启动时翻全表；</li>
 *   <li>已被归档的会话不继续；</li>
 *   <li>当前仍有活跃流的会话不继续（避免双跑）；</li>
 *   <li>开关 {@code session.auto_continue_on_restart} 关闭时完全不动作；</li>
 *   <li>延迟 {@value #START_DELAY_SECONDS} 秒执行：让 opencode 预热、数据库连接池就绪之后再发，
 *       否则补发的那一轮会因为运行时还没起来而直接失败。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAutoContinueService {

    /**
     * 配置分组（与 {@code SessionGlobalSettingProvider} 一致）
     */
    private static final String GROUP = "session";

    /**
     * 开关字段名
     */
    private static final String FIELD_AUTO_CONTINUE = "auto_continue_on_restart";

    /**
     * 补发内容
     */
    private static final String CONTINUE_PROMPT = "继续";

    /**
     * 单次扫描的会话上限
     */
    private static final int SCAN_LIMIT = 50;

    /**
     * 只处理最近这么多小时内有更新的会话
     */
    private static final long MAX_AGE_HOURS = 24L;

    /**
     * 启动后延迟多少秒再扫描
     */
    private static final long START_DELAY_SECONDS = 20L;

    /**
     * 会话表
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * 消息表：判断最后一轮是否缺助手回复
     */
    private final SysAiMessageMapper messageMapper;

    /**
     * 聊天编排：补发走与正常请求同一条链路（工具、用量、落库口径一致）
     */
    private final ChatStreamOrchestrator orchestrator;

    /**
     * 活跃流注册表：避免与正在跑的轮次双跑
     */
    private final ChatStreamRegistry registry;

    /**
     * 设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 启动完成后安排一次扫描。
     *
     * <p>排在预热（{@code AgentProviderFactory#warmUpOpencode}）之后，且再延迟
     * {@value #START_DELAY_SECONDS} 秒，确保补发的那一轮能真正跑起来。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void scheduleAfterRestart() {
        if (!settingReader.getBool(GROUP, FIELD_AUTO_CONTINUE, true)) {
            log.info("[AutoContinue] 重启自动继续已关闭，跳过");
            return;
        }
        CompletableFuture.delayedExecutor(START_DELAY_SECONDS, TimeUnit.SECONDS)
                .execute(this::scanAndContinue);
    }

    /**
     * 扫描未完成会话并逐个补发。
     *
     * <p>进入执行时<b>再实时读一次开关</b>：启动排程与真正扫描之间隔着
     * {@value #START_DELAY_SECONDS} 秒，若期间设置被改为关闭，此处能拦住
     * 已排队的扫描，保证「关闭立即生效、不用等下次重启」。</p>
     */
    void scanAndContinue() {
        if (!settingReader.getBool(GROUP, FIELD_AUTO_CONTINUE, true)) {
            log.info("[AutoContinue] 扫描开始时自动继续已被关闭，取消本次补发");
            return;
        }
        List<SysAiSession> sessions;
        try {
            sessions = sessionMapper.selectList(new LambdaQueryWrapper<SysAiSession>()
                    .ge(SysAiSession::getUpdateTime, LocalDateTime.now().minusHours(MAX_AGE_HOURS))
                    .orderByDesc(SysAiSession::getUpdateTime)
                    .last("LIMIT " + SCAN_LIMIT));
        } catch (Exception e) {
            log.warn("[AutoContinue] 扫描会话失败: {}", e.getMessage());
            return;
        }
        int continued = 0;
        for (SysAiSession session : sessions) {
            try {
                if (continueIfUnfinished(session)) {
                    continued++;
                }
            } catch (Exception e) {
                log.warn("[AutoContinue] 补发会话失败 sessionId={}: {}", session.getId(), e.getMessage());
            }
        }
        log.info("[AutoContinue] 扫描完成：候选 {} 个，补发 {} 个", sessions.size(), continued);
    }

    /**
     * 判断单个会话是否需要补发，需要则发起。
     *
     * @param session 会话
     * @return true 表示已发起补发
     */
    private boolean continueIfUnfinished(SysAiSession session) {
        if (session == null || session.getId() == null) {
            return false;
        }
        if (Boolean.TRUE.equals(session.getIsArchived())) {
            return false;
        }
        if (StringUtils.isBlank(session.getSysUserId())) {
            return false;
        }
        String sid = String.valueOf(session.getId());
        if (registry.get(sid) != null) {
            return false;
        }
        List<SysAiMessage> recent;
        try {
            recent = messageMapper.selectList(new LambdaQueryWrapper<SysAiMessage>()
                    .eq(SysAiMessage::getSysAiSessionId, session.getId())
                    .orderByDesc(SysAiMessage::getId)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("[AutoContinue] 读取会话消息失败 sessionId={}: {}", sid, e.getMessage());
            return false;
        }
        if (recent.isEmpty()) {
            return false;
        }
        SysAiMessage newest = recent.get(0);
        if (!"user".equalsIgnoreCase(newest.getRole())) {
            return false;
        }
        if (newest.getCreateTime() == null
                || newest.getCreateTime().isBefore(LocalDateTime.now().minusHours(MAX_AGE_HOURS))) {
            return false;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sid);
        body.put("content", CONTINUE_PROMPT);
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("content", CONTINUE_PROMPT);
        body.put("userMessage", userMessage);
        if (StringUtils.isNotBlank(session.getModelId())) {
            body.put("modelId", session.getModelId());
        }
        if (session.getCharacterId() != null) {
            body.put("characterId", String.valueOf(session.getCharacterId()));
        }

        UserResume resume = new UserResume();
        resume.setUserId(session.getSysUserId());
        log.info("[AutoContinue] 补发「继续」sessionId={} userId={} 模型={}",
                sid, session.getSysUserId(), session.getModelId());

        SilentSink sink = new SilentSink();
        try {
            AiUserContext.runWith(resume, () -> {
                synchronized (registry.lockFor(sid)) {
                PreparedChat prepared = orchestrator.prepare(body);
                registry.register(sid, session.getSysUserId(), sink);
                orchestrator.execute(prepared, sink);
                }
            });
        } catch (RuntimeException e) {
            // 准备阶段失败时不能留下活跃登记，否则这一会话在本次运行内再也不会被补发
            registry.remove(sid, sink);
            throw e;
        }
        return true;
    }

    /**
     * 后台补发没有前端连接，帧全部丢弃。
     *
     * <p>不实现成共享的 no-op 单例：每个补发轮次各持一份取消标记，
     * 停止接口命中时只影响对应那一轮。</p>
     */
    private static final class SilentSink implements ChatFrameSink {

        /**
         * 取消标记
         */
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public void sendEvent(Map<String, Object> event) {
            // 后台补发没有接收方
        }

        @Override
        public void sendTrace(AgentTrace trace) {
            // 后台补发没有接收方
        }

        @Override
        public void sendDone() {
            // 后台补发没有接收方
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public void markCancelled() {
            cancelled.set(true);
        }

        @Override
        public void close() {
            // 无传输可关
        }
    }
}
