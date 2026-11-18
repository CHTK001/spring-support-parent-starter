package com.chua.starter.ai.support.chat;

import com.chua.starter.account.channel.event.ChannelCatalogChangedEvent;
import com.chua.starter.ai.support.service.ChannelHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 渠道目录变更监听：把账号模块的数据变更翻译成路由缓存失效 + 健康状态复位。
 *
 * <p>渠道、渠道模型、渠道连接三项数据的读写全部归账号模块（{@code spring-support-account-starter}），
 * 但路由缓存（{@link MagpieRouteSource}、{@link ModelClientResolver}）和探针状态
 * （{@link ChannelHealthService}）属于 AI 运行时。账号模块改完数据只发布
 * {@link ChannelCatalogChangedEvent}，由本监听器承担 AI 侧的副作用，依赖方向保持
 * account ← ai 单向。</p>
 *
 * <p><b>为什么要复位健康状态</b>：令牌过期是常态；重新登录/续期成功后连接拿到的是新令牌，
 * 但旧状态可能仍是 {@code DOWN}，探针要连续成功 N 次才回到路由表，中间这段时间
 * 免费渠道里看不到它的模型。因此凭据变更的事件必须同时复位该连接状态。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelCatalogChangeListener {

    /**
     * Magpie 网关路由源
     */
    private final MagpieRouteSource magpieRouteSource;

    /**
     * 模型客户端解析器（内含 5 分钟路由缓存）
     */
    private final ModelClientResolver modelClientResolver;

    /**
     * 渠道健康探针服务
     */
    private final ChannelHealthService channelHealthService;

    /**
     * 处理渠道目录变更。
     *
     * <p><b>为什么是 after-commit</b>：变更通常发生在 {@code @Transactional} 写方法里，
     * 若在提交前就清缓存，并发请求会把「未提交的旧数据」重新读进缓存，反而把陈旧窗口
     * 拉长到下一个 TTL。事务内提交后执行，非事务发布（SSE 线程）则 {@code fallbackExecution}
     * 立即执行，两种路径都覆盖。</p>
     *
     * @param event 变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCatalogChanged(ChannelCatalogChangedEvent event) {
        invalidateRoutes(event.reason());
        String connectionId = event.connectionId();
        if (connectionId == null || connectionId.isBlank()) {
            return;
        }
        try {
            channelHealthService.resetState(connectionId);
        } catch (Exception e) {
            log.warn("[ChannelAuth] 复位渠道健康状态失败 connection={} : {}",
                    connectionId, e.getMessage());
        }
    }

    /**
     * 失效两处渠道路由缓存。
     *
     * @param reason 变更原因（日志用）
     */
    private void invalidateRoutes(String reason) {
        try {
            magpieRouteSource.invalidate();
        } catch (Exception e) {
            log.debug("[ChannelAuth] 清理 magpie 路由缓存失败 reason={} : {}", reason, e.getMessage());
        }
        try {
            modelClientResolver.invalidateRouteCache();
        } catch (Exception e) {
            log.debug("[ChannelAuth] 清理模型路由缓存失败 reason={} : {}", reason, e.getMessage());
        }
    }
}
