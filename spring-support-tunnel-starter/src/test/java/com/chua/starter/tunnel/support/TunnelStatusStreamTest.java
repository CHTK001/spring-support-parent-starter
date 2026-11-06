package com.chua.starter.tunnel.support;

import com.chua.starter.common.support.tunnel.TunnelStatusStream;
import com.chua.starter.common.support.tunnel.TunnelView;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实时状态推送器 {@link TunnelStatusStream} 的生命周期测试。
 *
 * <p>SSE 推送由单线程守护调度器驱动：订阅时立即推送快照，之后按固定间隔
 * 广播。连接断开只 removeFrom 消费者集合；广播时快照失败只记日志不拆除连接。
 * 关闭时停止调度并清空连接。</p>
 * @author CH
 * @since 4.0.0.51
 */
class TunnelStatusStreamTest {

    /**
     * 记录本次推送器内的快照调用次数，前两次成功，从第三次起生成失败。
     *
     * <p>前两次由 {@link #subscribe()} 和第一次 tick 消耗，第三次落在后续 tick
     * 上，验证广播失败不会移除订阅者。</p>
     */
    private static final class SnapshotTicker {

        private int calls;

        private List<TunnelView> tick() {
            if (++calls > 2) {
                throw new IllegalStateException("snapshot failed");
            }
            return List.of();
        }
    }

    /** 每两次 tick 之间至少 500ms（实现内部取 {@code Math.max(500, interval)}）。 */
    private static final long TICK_INTERVAL = 500L;
    /** 传给调度器的最小间隔：小于 500ms 下限，用于验证取 Max 的边界行为。 */
    private static final int INTERVAL_MIN = 200;

    private static TunnelStatusStream stream() throws Exception {
        return new TunnelStatusStream(new SnapshotTicker()::tick, INTERVAL_MIN);
    }

    private static boolean schedulerIsShutdown(TunnelStatusStream stream) throws Exception {
        Field field = TunnelStatusStream.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        return ((java.util.concurrent.ScheduledExecutorService) field.get(stream)).isShutdown();
    }

    @Test
    void shouldStartSubscribedAndBroadcast() throws Exception {
        TunnelStatusStream stream = stream();

        assertThat(stream.subscriberCount()).isEqualTo(0);

        SseEmitter emitter = stream.subscribe();
        assertThat(stream.subscriberCount()).isEqualTo(1);

        // 撑过三次 tick，其中第二次起快照失败，但连接还在。
        Thread.sleep(TICK_INTERVAL * 3);
        assertThat(stream.subscriberCount()).isEqualTo(1);

        stream.close();
        assertThat(stream.subscriberCount()).isEqualTo(0);
        assertThat(schedulerIsShutdown(stream)).isTrue();
    }

    @Test
    void shouldNotRemoveSubscriberWhenSnapshotFails() throws Exception {
        TunnelStatusStream stream = stream();

        SseEmitter emitter = stream.subscribe();
        assertThat(stream.subscriberCount()).isEqualTo(1);

        // 快照失败在广播层被吞掉（只记日志），连接不应被拆除。
        Thread.sleep(TICK_INTERVAL * 3);
        assertThat(stream.subscriberCount()).isEqualTo(1);

        stream.close();
        assertThat(stream.subscriberCount()).isEqualTo(0);
    }

    @Test
    void shouldTolerateMultipleSubscribers() throws Exception {
        TunnelStatusStream stream = stream();

        SseEmitter first = stream.subscribe();
        SseEmitter second = stream.subscribe();
        assertThat(stream.subscriberCount()).isEqualTo(2);

        Thread.sleep(TICK_INTERVAL * 3);
        assertThat(stream.subscriberCount()).isEqualTo(2);

        stream.close();
        assertThat(stream.subscriberCount()).isEqualTo(0);
    }

    @Test
    void shouldNotHangAfterClose() throws Exception {
        TunnelStatusStream stream = stream();

        stream.close();
        assertThat(stream.subscriberCount()).isEqualTo(0);

        // 关闭之后再次订阅应直接推送，不阻塞。
        SseEmitter emitter = stream.subscribe();
        assertThat(stream.subscriberCount()).isEqualTo(1);

        // 多次关闭安全。
        stream.close();
        assertThat(stream.subscriberCount()).isEqualTo(0);
    }
}
