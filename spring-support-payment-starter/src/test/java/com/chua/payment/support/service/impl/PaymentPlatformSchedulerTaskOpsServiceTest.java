package com.chua.payment.support.service.impl;

import com.chua.payment.support.vo.PaymentSchedulerTaskVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentPlatformSchedulerTaskOpsServiceTest {

    private static final String BASE_URL = "http://127.0.0.1:18083/scheduler/api";
    private static final String PLATFORM_LIST_URL = BASE_URL + "/v1/job-platform/payment/task/list";
    private static final String CONSOLE_LOGIN_URL = BASE_URL + "/job-console/auth/login";
    private static final String CONSOLE_LIST_URL = BASE_URL + "/job-console/api/payment/task/list";

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldUsePlatformApiWhenOAuthHeadersPresent() {
        PaymentPlatformSchedulerTaskOpsService service = createService(baseEnvironment());
        MockRestServiceServer server = createServer(service, true);
        bindRequestWithHeader("x-oauth-token", "oauth-token-123");

        server.expect(ExpectedCount.once(), requestTo(PLATFORM_LIST_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-oauth-token", "oauth-token-123"))
                .andRespond(withSuccess(taskListBody("2026-03-25 20:30:00"), MediaType.APPLICATION_JSON));

        List<PaymentSchedulerTaskVO> tasks = service.listTasks();

        assertEquals(1, tasks.size());
        assertEquals("payment-notify-cleanup", tasks.get(0).getTaskKey());
        assertEquals(LocalDateTime.of(2026, 3, 25, 20, 30, 0), tasks.get(0).getNextExecutionTime());
        server.verify();
    }

    @Test
    void shouldLoginToConsoleAndReuseSessionWhenNoOAuthHeadersPresent() {
        PaymentPlatformSchedulerTaskOpsService service = createService(baseEnvironment());
        MockRestServiceServer server = createServer(service, true);

        server.expect(ExpectedCount.once(), requestTo(CONSOLE_LOGIN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.password").value("admin123456"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.SET_COOKIE, "JSESSIONID=console-1; Path=/; HttpOnly")
                        .body(successEnvelope("null")));

        server.expect(ExpectedCount.twice(), requestTo(CONSOLE_LIST_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=console-1"))
                .andRespond(withSuccess(taskListBody("2026-03-25T20:45:00"), MediaType.APPLICATION_JSON));

        List<PaymentSchedulerTaskVO> firstCall = service.listTasks();
        List<PaymentSchedulerTaskVO> secondCall = service.listTasks();

        assertEquals(1, firstCall.size());
        assertEquals(1, secondCall.size());
        assertEquals(firstCall.get(0).getTaskKey(), secondCall.get(0).getTaskKey());
        server.verify();
    }

    @Test
    void shouldReloginToConsoleWhenSessionExpired() {
        PaymentPlatformSchedulerTaskOpsService service = createService(baseEnvironment());
        MockRestServiceServer server = createServer(service, false);

        server.expect(requestTo(CONSOLE_LOGIN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.SET_COOKIE, "JSESSIONID=console-old; Path=/; HttpOnly")
                        .body(successEnvelope("null")));

        server.expect(requestTo(CONSOLE_LIST_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=console-old"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"403\",\"message\":\"forbidden\"}"));

        server.expect(requestTo(CONSOLE_LOGIN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.SET_COOKIE, "JSESSIONID=console-new; Path=/; HttpOnly")
                        .body(successEnvelope("null")));

        server.expect(requestTo(CONSOLE_LIST_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=console-new"))
                .andRespond(withSuccess(taskListBody("1742907300000"), MediaType.APPLICATION_JSON));

        List<PaymentSchedulerTaskVO> tasks = service.listTasks();

        assertEquals(1, tasks.size());
        assertNotNull(tasks.get(0).getNextExecutionTime());
        server.verify();
    }

    private PaymentPlatformSchedulerTaskOpsService createService(MockEnvironment environment) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        ObjectProvider<RestTemplateBuilder> provider = beanFactory.getBeanProvider(RestTemplateBuilder.class);
        return new PaymentPlatformSchedulerTaskOpsService(environment, new ObjectMapper(), provider);
    }

    private MockRestServiceServer createServer(PaymentPlatformSchedulerTaskOpsService service, boolean ignoreExpectOrder) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate)
                .ignoreExpectOrder(ignoreExpectOrder)
                .build();
    }

    private MockEnvironment baseEnvironment() {
        return new MockEnvironment()
                .withProperty("plugin.payment.scheduler.platform-base-url", BASE_URL)
                .withProperty("plugin.payment.scheduler.platform-namespace", "payment");
    }

    private void bindRequestWithHeader(String headerName, String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(headerName, headerValue);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private String taskListBody(String nextExecutionTime) {
        return successEnvelope("[{" +
                "\"taskKey\":\"payment-notify-cleanup\"," +
                "\"taskName\":\"Notify Cleanup\"," +
                "\"cronExpression\":\"0 0/30 * * * ?\"," +
                "\"enabled\":true," +
                "\"description\":\"cleanup pending notifications\"," +
                "\"scheduled\":true," +
                "\"nextExecutionTime\":\"" + nextExecutionTime + "\"," +
                "\"lastRunStatus\":\"SUCCESS\"," +
                "\"lastRunMessage\":\"执行成功\"" +
                "}]");
    }

    private String successEnvelope(String data) {
        return "{\"code\":\"00000\",\"message\":\"操作成功\",\"data\":" + data + "}";
    }
}
