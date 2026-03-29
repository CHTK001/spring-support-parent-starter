package com.chua.starter.lock;

import com.chua.starter.lock.aspect.StrategyDistributedLockAspect;
import com.chua.starter.lock.aspect.StrategyIdempotentAspect;
import com.chua.starter.lock.configuration.LockAutoConfiguration;
import com.chua.starter.lock.configuration.LockRedisAutoConfiguration;
import com.chua.starter.lock.exception.IdempotentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.StreamUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = StrategyAnnotationCompatibilityTest.TestApplication.class)
class StrategyAnnotationCompatibilityTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private StrategyCompatibilityService strategyCompatibilityService;

    @BeforeEach
    void setUp() {
        strategyCompatibilityService.reset();
    }

    @Test
    void shouldExposeStrategyCompatibilityAspects() {
        assertNotNull(applicationContext.getBean(StrategyDistributedLockAspect.class));
        assertNotNull(applicationContext.getBean(StrategyIdempotentAspect.class));
        assertNotNull(applicationContext.getBean("lockStrategyAnnotationCompatibilityMarker"));
    }

    @Test
    void shouldSupportLegacyStrategyIdempotentAnnotation() {
        String first = strategyCompatibilityService.legacyIdempotent("legacy-1");

        assertThrows(com.chua.starter.lock.exception.IdempotentException.class,
                () -> strategyCompatibilityService.legacyIdempotent("legacy-1"));
        assertEquals("legacy-idempotent-1", first);
        assertEquals(1, strategyCompatibilityService.legacyIdempotentCount());
    }

    @Test
    void shouldSupportLegacyStrategyDistributedLockFallback() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<String> holding = executorService.submit(() ->
                strategyCompatibilityService.holdLegacyLock("legacy-2", entered, release));
        entered.await(5, TimeUnit.SECONDS);

        String fallback = strategyCompatibilityService.legacyLockFallback("legacy-2");
        release.countDown();

        assertEquals("legacy-fallback:legacy-2", fallback);
        assertEquals("legacy-hold:legacy-2", holding.get(5, TimeUnit.SECONDS));
        assertEquals(1, strategyCompatibilityService.legacyFallbackCount());
        executorService.shutdownNow();
    }

    @Test
    void shouldSupportLegacyStrategyBodyMd5Key() throws IOException {
        bindJsonRequest("/strategy/legacy/body", "{\"bizId\":1}");
        String first = strategyCompatibilityService.legacyBodyMd5();

        bindJsonRequest("/strategy/legacy/body", "{\"bizId\":2}");
        String second = strategyCompatibilityService.legacyBodyMd5();

        bindJsonRequest("/strategy/legacy/body", "{\"bizId\":2}");
        assertThrows(IdempotentException.class, () -> strategyCompatibilityService.legacyBodyMd5());

        assertEquals("legacy-body-1", first);
        assertEquals("legacy-body-2", second);
        assertEquals(2, strategyCompatibilityService.legacyBodyMd5Count());
    }

    private void bindJsonRequest(String uri, String body) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        StreamUtils.copyToByteArray(wrapper.getInputStream());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(wrapper));
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({LockRedisAutoConfiguration.class, LockAutoConfiguration.class})
    static class TestApplication {

        @Bean
        StrategyCompatibilityService strategyCompatibilityService() {
            return new StrategyCompatibilityService();
        }
    }

    static class StrategyCompatibilityService {
        private final AtomicInteger legacyIdempotentCounter = new AtomicInteger();
        private final AtomicInteger legacyFallbackCounter = new AtomicInteger();
        private final AtomicInteger legacyBodyMd5Counter = new AtomicInteger();

        @com.chua.starter.strategy.annotation.Idempotent(
                key = "#requestId",
                deleteOnSuccess = false,
                keyStrategy = com.chua.starter.strategy.annotation.Idempotent.KeyStrategy.SPEL
        )
        public String legacyIdempotent(String requestId) {
            return "legacy-idempotent-" + legacyIdempotentCounter.incrementAndGet();
        }

        @com.chua.starter.strategy.annotation.DistributedLock(
                key = "#businessKey",
                prefix = "legacy:",
                waitTime = 5,
                timeUnit = TimeUnit.SECONDS
        )
        public String holdLegacyLock(String businessKey, CountDownLatch entered, CountDownLatch release) throws InterruptedException {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "legacy-hold:" + businessKey;
        }

        @com.chua.starter.strategy.annotation.DistributedLock(
                key = "#businessKey",
                prefix = "legacy:",
                waitTime = 0,
                timeUnit = TimeUnit.SECONDS,
                failStrategy = com.chua.starter.strategy.annotation.DistributedLock.LockFailStrategy.FALLBACK,
                fallbackMethod = "legacyFallbackValue"
        )
        public String legacyLockFallback(String businessKey) {
            return "legacy-main:" + businessKey;
        }

        @com.chua.starter.strategy.annotation.Idempotent(
                keyStrategy = com.chua.starter.strategy.annotation.Idempotent.KeyStrategy.BODY_MD5
        )
        public String legacyBodyMd5() {
            return "legacy-body-" + legacyBodyMd5Counter.incrementAndGet();
        }

        public String legacyFallbackValue(String businessKey) {
            legacyFallbackCounter.incrementAndGet();
            return "legacy-fallback:" + businessKey;
        }

        int legacyIdempotentCount() {
            return legacyIdempotentCounter.get();
        }

        int legacyFallbackCount() {
            return legacyFallbackCounter.get();
        }

        int legacyBodyMd5Count() {
            return legacyBodyMd5Counter.get();
        }

        void reset() {
            legacyIdempotentCounter.set(0);
            legacyFallbackCounter.set(0);
            legacyBodyMd5Counter.set(0);
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
