package com.chua.starter.spider.support.security;

import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * 凭证安全策略测试。
 */
class CredentialSecurityTest {

    private CredentialSafetyChecker checker;
    private CredentialRedactor redactor;

    @BeforeEach
    void setUp() {
        checker = new CredentialSafetyChecker();
        redactor = new CredentialRedactor();
    }

    // ── CredentialSafetyChecker ───────────────────────────────────────────────

    @Test
    void check_taskWithPlaintextPasswordInExecutionPolicy_returnsWarning() {
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskName("test")
                .executionPolicy("{\"password\":\"abc12345\"}")
                .build();
        List<String> warnings = checker.check(task);
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.get(0)).contains("executionPolicy");
    }

    @Test
    void check_taskWithNoSensitiveFields_returnsEmpty() {
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskName("test")
                .executionPolicy("{\"threadCount\":4,\"cron\":\"0 * * * * ?\"}")
                .build();
        assertThat(checker.check(task)).isEmpty();
    }

    @Test
    void check_nullTask_returnsEmpty() {
        assertThat(checker.check(null)).isEmpty();
    }

    @Test
    void checkNode_nodeConfigWithPlaintextPassword_returnsWarning() {
        SpiderFlowNode node = SpiderFlowNode.builder()
                .nodeId("dl-1")
                .nodeType(SpiderNodeType.DOWNLOADER)
                .config(Map.of("password", "Secret123", "url", "https://example.com"))
                .build();
        List<String> warnings = checker.checkNode(node);
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.get(0)).contains("dl-1");
    }

    @Test
    void checkNode_nodeConfigWithCredentialRef_returnsEmpty() {
        SpiderFlowNode node = SpiderFlowNode.builder()
                .nodeId("dl-1")
                .nodeType(SpiderNodeType.DOWNLOADER)
                .config(Map.of("credentialId", "cred-001", "credentialType", "COOKIE"))
                .build();
        // credentialId 不是敏感 key，credentialType 也不是密码值
        assertThat(checker.checkNode(node)).isEmpty();
    }

    @Test
    void checkNode_nullNode_returnsEmpty() {
        assertThat(checker.checkNode(null)).isEmpty();
    }

    @Test
    void checkMap_withPlaintextSecret_returnsWarning() {
        Map<String, Object> config = Map.of("apiKey", "mySecret99", "timeout", 5000);
        List<String> warnings = checker.checkMap(config, "config");
        assertThat(warnings).isNotEmpty();
    }

    @Test
    void checkMap_withPlaceholderValue_returnsEmpty() {
        // ${...} 格式是引用，不是明文
        Map<String, Object> config = Map.of("password", "${env.PASSWORD}");
        assertThat(checker.checkMap(config, "config")).isEmpty();
    }

    // ── CredentialRedactor ────────────────────────────────────────────────────

    @Test
    void redactJson_passwordField_isReplaced() {
        String json = "{\"taskName\":\"test\",\"password\":\"abc12345\"}";
        String redacted = redactor.redactJson(json);
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("abc12345");
    }

    @Test
    void redactJson_multipleFields_allReplaced() {
        String json = "{\"password\":\"pass123\",\"apiKey\":\"key456\",\"name\":\"test\"}";
        String redacted = redactor.redactJson(json);
        assertThat(redacted).doesNotContain("pass123");
        assertThat(redacted).doesNotContain("key456");
        assertThat(redacted).contains("test"); // 非敏感字段保留
    }

    @Test
    void redactJson_noSensitiveFields_unchanged() {
        String json = "{\"taskName\":\"test\",\"threadCount\":4}";
        String redacted = redactor.redactJson(json);
        assertThat(redacted).isEqualTo(json);
    }

    @Test
    void redactJson_nullInput_returnsNull() {
        assertThat(redactor.redactJson(null)).isNull();
    }

    @Test
    void redactLogLine_passwordInLog_isReplaced() {
        String log = "Connecting with password=myPass123 to host";
        String redacted = redactor.redactLogLine(log);
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("myPass123");
    }

    @Test
    void redactLogLine_noSensitiveData_unchanged() {
        String log = "Task started at 2026-04-15 with threadCount=4";
        assertThat(redactor.redactLogLine(log)).isEqualTo(log);
    }
}
