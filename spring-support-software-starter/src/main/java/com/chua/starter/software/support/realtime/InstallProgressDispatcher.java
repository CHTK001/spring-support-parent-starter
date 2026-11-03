package com.chua.starter.software.support.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 安装进度分发器
 *
 * <p>把「什么时候允许把进度发出去」这件事从业务服务里抽出来，只做两件事：</p>
 * <ol>
 *   <li>事务活跃时，把事件挂到<b>提交之后</b>再发，回滚则整帧丢弃；</li>
 *   <li>无事务时立即发。</li>
 * </ol>
 *
 * <p><b>为什么必须等提交</b>：安装、卸载、更新都跑在 {@code TransactionTemplate} 里。
 * 若在事务内就广播「安装完成 100%」，而之后某条落库语句失败导致回滚，前端会先看到
 * 一个数据库里并不存在的成功状态，刷新列表又变回失败。这种前后矛盾比晚几百毫秒更糟，
 * 所以宁可不发，也不发一条事后必须撤回的帧。</p>
 *
 * <p><b>为什么异常要吞</b>：推送只是「告诉页面」，安装本身已经做完了。让推送失败
 * 反过来把已经成功的安装判成失败，是本末倒置。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class InstallProgressDispatcher {

    /**
     * 进度发布器
     */
    private final InstallProgressPublisher publisher;

    /**
     * 创建分发器
     *
     * @param publisher 发布器，可为 {@code null}（等价于静默丢弃）
     */
    public InstallProgressDispatcher(InstallProgressPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 分发一帧进度
     *
     * @param event 进度事件，可为 {@code null}（直接忽略）
     */
    public void dispatch(InstallProgressEvent event) {
        if (event == null || publisher == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safePublish(event);
                }
            });
            return;
        }
        safePublish(event);
    }

    /**
     * 发布并吞掉异常
     *
     * @param event 进度事件
     */
    private void safePublish(InstallProgressEvent event) {
        try {
            publisher.publish(event);
        } catch (Exception e) {
            log.warn("[Software][Progress] 进度推送失败: recordId={}, reason={}",
                    event.recordId(), e.getMessage());
        }
    }
}
