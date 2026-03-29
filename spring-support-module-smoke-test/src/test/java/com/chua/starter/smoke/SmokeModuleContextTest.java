package com.chua.starter.smoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.ClassUtils;
import com.chua.starter.smoke.web.SmokeController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SmokeModuleContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SmokeController smokeController;

    @Autowired(required = false)
    private HealthEndpoint healthEndpoint;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext);
        assertNotNull(smokeController);
    }

    @Test
    void smokeControllerShouldExposeTargetModule() {
        var response = smokeController.ping();

        assertNotNull(response);
        assertNotNull(response.get("status"));
        assertNotNull(response.get("module"));
        org.junit.jupiter.api.Assertions.assertEquals("ok", response.get("status"));
        org.junit.jupiter.api.Assertions.assertEquals(environment.getProperty("smoke.target.module", "base"), response.get("module"));
    }

    @Test
    void actuatorHealthEndpointShouldBeRegistered() {
        assertNotNull(healthEndpoint);
    }

    @Test
    void strategyConsoleStatusEndpointShouldBeReachable() throws Exception {
        Assumptions.assumeTrue(isStrategyModule());

        mockMvc.perform(get("/v2/strategy/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authEnabled").value(true))
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }

    @Test
    void strategyMetricsEndpointShouldRequireAuthentication() throws Exception {
        Assumptions.assumeTrue(isStrategyModule());

        mockMvc.perform(get("/v2/strategy/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":\"401\"")));
    }

    @Test
    void strategyConsolePageShouldRedirectToLoginWhenUnauthenticated() throws Exception {
        Assumptions.assumeTrue(isStrategyModule());

        mockMvc.perform(get("/strategy-console/index.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/strategy-console/login.html")));
    }

    @Test
    void strategyMetricsEndpointShouldBeReachableAfterLogin() throws Exception {
        Assumptions.assumeTrue(isStrategyModule());

        MvcResult loginResult = mockMvc.perform(post("/v2/strategy/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "admin123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(get("/v2/strategy/metrics").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").exists())
                .andExpect(jsonPath("$.code").value("00000"));
    }

    @Test
    void strategyLockCompatibilityAspectsShouldBeResolvedByClasspath() {
        Assumptions.assumeTrue(isStrategyModule());

        boolean lockCompatibilityEnabled = applicationContext.containsBean("lockStrategyAnnotationCompatibilityMarker")
                && hasType("com.chua.starter.lock.aspect.StrategyDistributedLockAspect")
                && hasType("com.chua.starter.lock.aspect.StrategyIdempotentAspect");

        if (lockCompatibilityEnabled) {
            assertEquals(0, beanCount("com.chua.starter.strategy.aspect.DistributedLockAspect"));
            assertEquals(0, beanCount("com.chua.starter.strategy.aspect.IdempotentAspect"));
            assertEquals(1, beanCount("com.chua.starter.lock.aspect.StrategyDistributedLockAspect"));
            assertEquals(1, beanCount("com.chua.starter.lock.aspect.StrategyIdempotentAspect"));
            return;
        }

        assertTrue(hasType("com.chua.starter.strategy.aspect.DistributedLockAspect"));
        assertTrue(hasType("com.chua.starter.strategy.aspect.IdempotentAspect"));
        assertEquals(1, beanCount("com.chua.starter.strategy.aspect.DistributedLockAspect"));
        assertEquals(1, beanCount("com.chua.starter.strategy.aspect.IdempotentAspect"));
        assertFalse(hasType("com.chua.starter.lock.aspect.StrategyDistributedLockAspect"));
        assertFalse(hasType("com.chua.starter.lock.aspect.StrategyIdempotentAspect"));
    }

    @Test
    void proxyServerPageEndpointShouldReturnSeededServer() throws Exception {
        Assumptions.assumeTrue(isProxyModule());

        mockMvc.perform(get("/proxy/server/page")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].systemServerName").value("smoke-proxy-server"))
                .andExpect(jsonPath("$.data.records[0].systemServerType").value("HTTP"))
                .andExpect(jsonPath("$.data.records[0].filterCount").value(1));
    }

    @Test
    void proxyStatisticsEndpointShouldReturnSeededCounts() throws Exception {
        Assumptions.assumeTrue(isProxyModule());

        mockMvc.perform(get("/proxy/server/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.running").value(0))
                .andExpect(jsonPath("$.data.stopped").value(1))
                .andExpect(jsonPath("$.data.error").value(0));
    }

    private boolean isStrategyModule() {
        return "spring-support-strategy-starter".equals(
                environment.getProperty("smoke.target.module", "base"));
    }

    private boolean isProxyModule() {
        return "spring-support-proxy-starter".equals(
                environment.getProperty("smoke.target.module", "base"));
    }

    private boolean hasType(String className) {
        return beanCount(className) > 0;
    }

    private int beanCount(String className) {
        ClassLoader classLoader = applicationContext.getClassLoader();
        if (!ClassUtils.isPresent(className, classLoader)) {
            return 0;
        }
        Class<?> type = ClassUtils.resolveClassName(className, classLoader);
        return applicationContext.getBeanNamesForType(type).length;
    }
}
