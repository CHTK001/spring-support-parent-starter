package com.chua.starter.lock;

import com.chua.common.support.concurrent.lock.Locked;
import com.chua.starter.lock.configuration.LockAutoConfiguration;
import com.chua.starter.lock.configuration.LockRedisAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = LockedAspectTest.TestApplication.class)
class LockedAspectTest {

    @Autowired
    private LockedTestService lockedTestService;

    @BeforeEach
    void setUp() {
        lockedTestService.reset();
    }

    @Test
    void shouldSerializeInvocationsWithTheSameLockKey() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<String> first = executorService.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return lockedTestService.serialize("order-1");
        });
        Future<String> second = executorService.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return lockedTestService.serialize("order-1");
        });

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        assertEquals("ok:order-1", first.get(5, TimeUnit.SECONDS));
        assertEquals("ok:order-1", second.get(5, TimeUnit.SECONDS));
        assertEquals(2, lockedTestService.executionCount());
        assertEquals(1, lockedTestService.maxConcurrent());
        executorService.shutdownNow();
    }

    @Test
    void shouldInvokeFallbackWhenLockCannotBeAcquiredImmediately() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<String> holding = executorService.submit(() -> lockedTestService.holdForFallback("order-2", entered, release));
        entered.await(5, TimeUnit.SECONDS);

        String fallbackValue = lockedTestService.fallbackWhenBusy("order-2");
        release.countDown();

        assertEquals("fallback:order-2", fallbackValue);
        assertEquals("hold:order-2", holding.get(5, TimeUnit.SECONDS));
        assertEquals(1, lockedTestService.fallbackCount());
        executorService.shutdownNow();
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({LockRedisAutoConfiguration.class, LockAutoConfiguration.class})
    static class TestApplication {

        @Bean
        LockedTestService lockedTestService() {
            return new LockedTestService();
        }
    }

    static class LockedTestService {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final AtomicInteger executionCount = new AtomicInteger();
        private final AtomicInteger fallbackCount = new AtomicInteger();

        @Locked(name = "lock:serialize", keys = "#businessKey", waitTime = "2S")
        public String serialize(String businessKey) throws InterruptedException {
            int current = active.incrementAndGet();
            maxConcurrent.updateAndGet(existing -> Math.max(existing, current));
            executionCount.incrementAndGet();
            Thread.sleep(150L);
            active.decrementAndGet();
            return "ok:" + businessKey;
        }

        @Locked(name = "lock:fallback", keys = "#businessKey", waitTime = "2S")
        public String holdForFallback(String businessKey, CountDownLatch entered, CountDownLatch release) throws InterruptedException {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "hold:" + businessKey;
        }

        @Locked(name = "lock:fallback", keys = "#businessKey", waitTime = "0", throwException = false, fallbackMethod = "fallbackValue")
        public String fallbackWhenBusy(String businessKey) {
            return "main:" + businessKey;
        }

        public String fallbackValue(String businessKey) {
            fallbackCount.incrementAndGet();
            return "fallback:" + businessKey;
        }

        int executionCount() {
            return executionCount.get();
        }

        int maxConcurrent() {
            return maxConcurrent.get();
        }

        int fallbackCount() {
            return fallbackCount.get();
        }

        void reset() {
            active.set(0);
            maxConcurrent.set(0);
            executionCount.set(0);
            fallbackCount.set(0);
        }
    }
}
