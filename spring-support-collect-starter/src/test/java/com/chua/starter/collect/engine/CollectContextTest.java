package com.chua.starter.collect.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.properties.CollectProperties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 采集记录计数测试。
 *
 * <p>「总采集数量」必须等于真正写进结果表的行数。产出计数在
 * {@link CollectContext#offerRecord} 里先记后写，唯一键挡下的重复行没有进库；
 * 因此 {@link CollectContext#discardRecords(int)} 负责把没写成的条数收回来，
 * 否则流程累计会一路虚高（古诗流程实测 86,939 对表内 55,593）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class CollectContextTest {

    /**
     * 构造最小可用的根上下文。
     *
     * @param properties 平台配置
     * @return 根上下文
     */
    private static CollectContext context(CollectProperties properties) {
        return new CollectContext(1, 1L, "T1", properties, new CollectSink() {
            @Override
            public void nodeLog(CollectNode node, int iteration, String status, long costMs,
                                String message, String preview) {
                // 测试不需要节点日志
            }

            @Override
            public void record(CollectRecordEntry entry) {
                // 测试不需要落库
            }

            @Override
            public CollectSink forFlow(Integer flowId) {
                return this;
            }
        });
    }

    @Test
    @DisplayName("产出一条记一条，收回后计数只留真正入库的条数")
    void discardRecordsKeepsOnlyWrittenRows() {
        CollectContext context = context(new CollectProperties());
        for (int i = 0; i < 3; i++) {
            assertTrue(context.offerRecord(new CollectRecordEntry()));
        }
        assertEquals(3, context.getRecordCount().get());

        // 唯一键挡下 2 条：计数要退回 1，不能把重复行算成采集结果
        context.discardRecords(2);
        assertEquals(1, context.getRecordCount().get());
    }

    @Test
    @DisplayName("收回 0 或负数不做任何事，计数不会反超")
    void ignoreNonPositiveDiscard() {
        CollectContext context = context(new CollectProperties());
        context.offerRecord(new CollectRecordEntry());

        context.discardRecords(0);
        context.discardRecords(-5);

        assertEquals(1, context.getRecordCount().get());
    }

    @Test
    @DisplayName("子流程产出的记录计入父任务，收回时父级同步扣减")
    void nestedContextSharesCounter() {
        CollectContext parent = context(new CollectProperties());
        CollectContext child = CollectContext.nested(parent, 2);

        assertTrue(child.offerRecord(new CollectRecordEntry()));
        assertEquals(1, parent.getRecordCount().get(), "子流程产出计入父任务");
        assertEquals(1, child.getRecordCount().get(), "子流程自己也有一份本层计数");

        child.discardRecords(1);
        assertEquals(0, parent.getRecordCount().get(), "父任务同步扣减");
        assertEquals(0, child.getRecordCount().get(), "子流程本层计数同步扣减");
    }

    @Test
    @DisplayName("超过单任务上限的记录不计数")
    void dropRecordsBeyondLimit() {
        CollectProperties properties = new CollectProperties();
        properties.setMaxRecordPerTask(2);
        CollectContext context = context(properties);

        assertTrue(context.offerRecord(new CollectRecordEntry()));
        assertTrue(context.offerRecord(new CollectRecordEntry()));
        assertFalse(context.offerRecord(new CollectRecordEntry()), "第三条被上限丢弃");

        assertEquals(2, context.getRecordCount().get());
    }

    @Test
    @DisplayName("并发产出与收回后计数仍为净写入条数")
    void concurrentOfferAndDiscard() throws InterruptedException {
        CollectContext context = context(new CollectProperties());
        int threads = 4;
        // [P3C A20 豁免]
        int perThread = 250;
        AtomicInteger dropped = new AtomicInteger();
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
// [P3C C1 豁免] 测试代码，验证线程行为，不必走线程池
            workers[t] = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    if (!context.offerRecord(new CollectRecordEntry())) {
                        dropped.incrementAndGet();
                    }
                }
                context.discardRecords(perThread / 2);
            });
            workers[t].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        // 每线程写 250、退回 125 → 净 125；四线程合计 500
        assertEquals(0, dropped.get());
        assertEquals(threads * (perThread / 2), context.getRecordCount().get());
    }
}
