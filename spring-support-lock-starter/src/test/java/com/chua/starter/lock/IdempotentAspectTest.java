package com.chua.starter.lock;

import com.chua.starter.lock.annotation.Idempotent;
import com.chua.starter.lock.configuration.LockAutoConfiguration;
import com.chua.starter.lock.configuration.LockRedisAutoConfiguration;
import com.chua.starter.lock.exception.IdempotentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StreamUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
        classes = IdempotentAspectTest.TestApplication.class,
        properties = "plugin.lock.idempotent.provider=local"
)
class IdempotentAspectTest {

    @Autowired
    private IdempotentTestService idempotentTestService;

    @BeforeEach
    void setUp() {
        idempotentTestService.reset();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldReturnPreviousResultForDuplicateRequest() {
        String first = idempotentTestService.returnPrevious("request-1");
        String second = idempotentTestService.returnPrevious("request-1");

        assertEquals(first, second);
        assertEquals(1, idempotentTestService.returnPreviousCount());
    }

    @Test
    void shouldAllowRetryAfterFailure() {
        assertThrows(IllegalStateException.class, () -> idempotentTestService.failThenRetry("request-2"));

        String result = idempotentTestService.failThenRetry("request-2");
        assertEquals("retry-2", result);
    }

    @Test
    void shouldRemoveKeyWhenDeleteOnSuccessEnabled() {
        String first = idempotentTestService.deleteOnSuccess("request-3");
        String second = idempotentTestService.deleteOnSuccess("request-3");

        assertEquals("delete-1", first);
        assertEquals("delete-2", second);
    }

    @Test
    void shouldThrowOnDuplicateByDefault() {
        idempotentTestService.throwOnDuplicate("request-4");

        assertThrows(IdempotentException.class, () -> idempotentTestService.throwOnDuplicate("request-4"));
    }

    @Test
    void shouldInvokeFallbackWhenDuplicateStrategyIsFallback() {
        String first = idempotentTestService.fallbackOnDuplicate("request-5");
        String second = idempotentTestService.fallbackOnDuplicate("request-5");

        assertEquals("fallback-main-1", first);
        assertEquals("fallback-duplicate-request-5", second);
        assertEquals(1, idempotentTestService.fallbackMainCount());
        assertEquals(1, idempotentTestService.fallbackDuplicateCount());
    }

    @Test
    void shouldSupportHeaderFunctionInSpelKey() throws IOException {
        bindJsonRequest("/idempotent/header", "{\"id\":1}", request -> request.addHeader("X-Request-Id", "req-1"));
        String first = idempotentTestService.headerBased();

        bindJsonRequest("/idempotent/header", "{\"id\":2}", request -> request.addHeader("X-Request-Id", "req-1"));
        String second = idempotentTestService.headerBased();

        bindJsonRequest("/idempotent/header", "{\"id\":3}", request -> request.addHeader("X-Request-Id", "req-2"));
        String third = idempotentTestService.headerBased();

        assertEquals("header-1", first);
        assertEquals(first, second);
        assertEquals("header-2", third);
        assertEquals(2, idempotentTestService.headerBasedCount());
    }

    @Test
    void shouldDifferentiateRequestsByBodyWhenUsingBodyMd5() throws IOException {
        bindJsonRequest("/idempotent/body", "{\"bizId\":1}", null);
        String first = idempotentTestService.bodyBased();

        bindJsonRequest("/idempotent/body", "{\"bizId\":2}", null);
        String second = idempotentTestService.bodyBased();

        bindJsonRequest("/idempotent/body", "{\"bizId\":2}", null);
        String third = idempotentTestService.bodyBased();

        assertEquals("body-1", first);
        assertEquals("body-2", second);
        assertEquals(second, third);
        assertEquals(2, idempotentTestService.bodyBasedCount());
    }

    private void bindJsonRequest(String uri, String body, Consumer<MockHttpServletRequest> customizer) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (customizer != null) {
            customizer.accept(request);
        }

        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        StreamUtils.copyToByteArray(wrapper.getInputStream());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(wrapper));
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({LockRedisAutoConfiguration.class, LockAutoConfiguration.class})
    static class TestApplication {

        @Bean
        IdempotentTestService idempotentTestService() {
            return new IdempotentTestService();
        }
    }

    static class IdempotentTestService {
        private final AtomicInteger returnPreviousCounter = new AtomicInteger();
        private final AtomicInteger deleteCounter = new AtomicInteger();
        private final AtomicInteger retryCounter = new AtomicInteger();
        private final AtomicInteger throwCounter = new AtomicInteger();
        private final AtomicInteger headerCounter = new AtomicInteger();
        private final AtomicInteger bodyCounter = new AtomicInteger();
        private final AtomicInteger fallbackMainCounter = new AtomicInteger();
        private final AtomicInteger fallbackDuplicateCounter = new AtomicInteger();

        @Idempotent(key = "#requestId", duplicateStrategy = Idempotent.DuplicateStrategy.RETURN_PREVIOUS)
        public String returnPrevious(String requestId) {
            return "value-" + returnPreviousCounter.incrementAndGet();
        }

        @Idempotent(key = "#requestId", deleteOnSuccess = true)
        public String deleteOnSuccess(String requestId) {
            return "delete-" + deleteCounter.incrementAndGet();
        }

        @Idempotent(key = "#requestId")
        public String failThenRetry(String requestId) {
            int attempt = retryCounter.incrementAndGet();
            if (attempt == 1) {
                throw new IllegalStateException("boom");
            }
            return "retry-" + attempt;
        }

        @Idempotent(key = "#requestId")
        public String throwOnDuplicate(String requestId) {
            return "throw-" + throwCounter.incrementAndGet();
        }

        @Idempotent(key = "#header('X-Request-Id')", duplicateStrategy = Idempotent.DuplicateStrategy.RETURN_PREVIOUS)
        public String headerBased() {
            return "header-" + headerCounter.incrementAndGet();
        }

        @Idempotent(keyStrategy = Idempotent.KeyStrategy.BODY_MD5, duplicateStrategy = Idempotent.DuplicateStrategy.RETURN_PREVIOUS)
        public String bodyBased() {
            return "body-" + bodyCounter.incrementAndGet();
        }

        @Idempotent(
                key = "#requestId",
                duplicateStrategy = Idempotent.DuplicateStrategy.FALLBACK,
                fallbackMethod = "fallbackDuplicateValue"
        )
        public String fallbackOnDuplicate(String requestId) {
            return "fallback-main-" + fallbackMainCounter.incrementAndGet();
        }

        public String fallbackDuplicateValue(String requestId) {
            fallbackDuplicateCounter.incrementAndGet();
            return "fallback-duplicate-" + requestId;
        }

        int returnPreviousCount() {
            return returnPreviousCounter.get();
        }

        int headerBasedCount() {
            return headerCounter.get();
        }

        int bodyBasedCount() {
            return bodyCounter.get();
        }

        int fallbackMainCount() {
            return fallbackMainCounter.get();
        }

        int fallbackDuplicateCount() {
            return fallbackDuplicateCounter.get();
        }

        void reset() {
            returnPreviousCounter.set(0);
            deleteCounter.set(0);
            retryCounter.set(0);
            throwCounter.set(0);
            headerCounter.set(0);
            bodyCounter.set(0);
            fallbackMainCounter.set(0);
            fallbackDuplicateCounter.set(0);
        }
    }
}
