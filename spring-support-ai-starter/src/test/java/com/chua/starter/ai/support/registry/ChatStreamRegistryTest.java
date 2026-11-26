package com.chua.starter.ai.support.registry;

import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.stream.ChatStreamRegistry;
import com.chua.starter.ai.support.stream.SseChatFrameSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用真实帧槽验证会话互斥、归属与迟到清理。
 * @author CH
 */
class ChatStreamRegistryTest {
    /** 延迟完成不能删除新轮次；并发登记不能覆盖旧槽。 */
    @Test
    void lateCleanupPreservesNextRunAndRejectsOverwrite() {
        ChatStreamRegistry registry = new ChatStreamRegistry();
        var old = new SseChatFrameSink(new ObjectMapper(), new ByteArrayOutputStream(), null);
        var next = new SseChatFrameSink(new ObjectMapper(), new ByteArrayOutputStream(), null);
        registry.register("session", "owner", old);
        assertThrows(ApiException.class, () -> registry.register("session", "owner", next));
        assertSame(old, registry.get("session").sink());
        registry.remove("session", old);
        registry.register("session", "owner", next);
        registry.remove("session", old);
        assertSame(next, registry.get("session").sink());
        assertThrows(ApiException.class, () -> registry.requestStop("session", "other"));
        registry.requestStop("session", "owner");
        assertTrue(next.isCancelled());
        assertNull(registry.get("session"));
    }

    /** 同会话准备与注册在锁内只允许一个线程启动。 */
    @Test
    void preparationAndRegistrationHaveOneWinner() throws Exception {
        ChatStreamRegistry registry = new ChatStreamRegistry();
        AtomicInteger started = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Runnable action = () -> {
                synchronized (registry.lockFor("session")) {
                    if (registry.get("session") != null) return;
                    registry.register("session", "owner", new SseChatFrameSink(new ObjectMapper(), new ByteArrayOutputStream(), null));
                    started.incrementAndGet();
                }
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            first.get(); second.get();
        }
        assertEquals(1, started.get());
    }
}
