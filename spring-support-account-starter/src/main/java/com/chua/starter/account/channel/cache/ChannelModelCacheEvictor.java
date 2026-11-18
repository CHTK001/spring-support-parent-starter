package com.chua.starter.account.channel.cache;

import com.chua.starter.account.channel.event.ChannelCatalogChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 渠道模型缓存失效器：账号侧写操作提交后清空模型清单缓存。
 *
 * <p><b>为什么是 after-commit</b>：模型/渠道的写方法都在 {@code @Transactional} 内执行，
 * 提交前清缓存会让并发请求把「尚未提交的旧数据」重新读进缓存，反而把陈旧窗口拉长到下一次 TTL。
 * 提交后失效则保证下一次读取必然命中新数据；非事务发布（如外部直接调用）由
 * {@code fallbackExecution} 立即执行。</p>
 *
 * <p>失效粒度为整表：模型清单是一份小快照，按渠道精确失效需要事件携带渠道标识，
 * 而现有事件只带原因串；整体清空后由首次读取重建，代价远小于维护两套失效口径。</p>
 *
 * @author CH
 * @since 4.0.0.59
 */
@Slf4j
@RequiredArgsConstructor
public class ChannelModelCacheEvictor {

    /**
     * 渠道模型清单缓存
     */
    private final AccountChannelModelCache modelCache;

    /**
     * 渠道目录变更提交后失效模型清单缓存。
     *
     * @param event 变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCatalogChanged(ChannelCatalogChangedEvent event) {
        modelCache.clear();
        log.debug("[account] 渠道目录变更，模型清单缓存已失效 reason={}", event.reason());
    }
}
