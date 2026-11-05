// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.software.support.realtime;

import com.chua.starter.software.support.entity.SoftInstallRecord;
import com.chua.starter.software.support.enums.InstallStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InstallProgressDispatcher} 单元测试
 *
 * <p>核心行为是「事务提交后才发、回滚则不发」，这一点用真实
 * {@link TransactionSynchronizationManager} 驱动即可验证，
 * 不需要真实数据库或 Spring 容器（本仓也禁用 mockito）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class InstallProgressDispatcherTest {

    /**
     * 无事务时立即发送
     */
    @Test
    void dispatchWithoutTransactionShouldPublishImmediately() {
        RecorderPublisher publisher = new RecorderPublisher();
        InstallProgressDispatcher dispatcher = new InstallProgressDispatcher(publisher);

        dispatcher.dispatch(event());

        assertEquals(1, publisher.events.size());
    }

    /**
     * 事务活跃时不立刻发送，而是登记到提交后
     */
    @Test
    void dispatchInTransactionShouldDeferUntilCommit() {
        RecorderPublisher publisher = new RecorderPublisher();
        InstallProgressDispatcher dispatcher = new InstallProgressDispatcher(publisher);

        TransactionSynchronizationManager.initSynchronization();
        try {
            dispatcher.dispatch(event());
            assertTrue(publisher.events.isEmpty(), "事务未提交前不应发出进度帧");

            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, syncs.size());

            // 模拟提交：Spring 在提交完成后会回调 afterCommit
            syncs.forEach(TransactionSynchronization::afterCommit);
            assertEquals(1, publisher.events.size());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 回滚时整帧丢弃：不能让前端看到一条数据库里并不存在的成功状态
     */
    @Test
    void dispatchInTransactionShouldDropOnRollback() {
        RecorderPublisher publisher = new RecorderPublisher();
        InstallProgressDispatcher dispatcher = new InstallProgressDispatcher(publisher);

        TransactionSynchronizationManager.initSynchronization();
        try {
            dispatcher.dispatch(event());
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();

            // 模拟回滚：只走 afterCompletion(ROLLED_BACK)，不触发 afterCommit
            syncs.forEach(sync ->
                    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            assertTrue(publisher.events.isEmpty(), "回滚后不应把进度发出去");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 发布器抛异常时不能向外扩散：推送只是提示，安装本身已经成功
     */
    @Test
    void publishFailureShouldNotPropagate() {
        InstallProgressDispatcher dispatcher = new InstallProgressDispatcher(event -> {
            throw new IllegalStateException("boom");
        });

        dispatcher.dispatch(event());
    }

    /**
     * 发布器缺失（宿主未启用实时通道）等价于静默丢弃
     */
    @Test
    void nullPublisherShouldDropSilently() {
        InstallProgressDispatcher dispatcher = new InstallProgressDispatcher(null);
        dispatcher.dispatch(event());
        dispatcher.dispatch(null);
    }

    /**
     * 构造一条 30% 安装中的事件
     *
     * @return 进度事件
     */
    private static InstallProgressEvent event() {
        SoftInstallRecord record = new SoftInstallRecord();
        record.setInstallRecordId("record-1");
        record.setInstallRecordSoftwareId("software-1");
        record.setInstallRecordUserId("user-1");
        record.setInstallRecordOperation("install");
        record.setInstallRecordStatus(InstallStatus.INSTALLING.getValue());
        record.setInstallRecordProgress(30);
        return InstallProgressEvent.of(record, "准备安装");
    }

    /**
     * 记录发布内容的发布器桩
     */
    private static final class RecorderPublisher implements InstallProgressPublisher {

        /**
         * 已发出的帧
         */
        private final List<InstallProgressEvent> events = new ArrayList<>();

        @Override
        public void publish(InstallProgressEvent event) {
            events.add(event);
        }
    }
}