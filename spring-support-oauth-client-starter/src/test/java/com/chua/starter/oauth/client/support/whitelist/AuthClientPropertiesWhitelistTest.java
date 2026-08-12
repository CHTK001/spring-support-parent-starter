package com.chua.starter.oauth.client.support.whitelist;

import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OAuth 动态白名单单元测试。
 *
 * <p>覆盖 {@link AuthClientProperties} 的动态白名单能力:
 * addWhitelist / removeWhitelist / clearDynamicWhitelist / getEffectiveWhitelist。
 * 验证内置默认白名单 + yaml 自定义 + 动态白名单三层合并的语义,
 * 以及 Ant 风格 URL Pattern 的动态增删。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("OAuth 动态白名单单元测试")
class AuthClientPropertiesWhitelistTest {

    @Test
    @DisplayName("getEffectiveWhitelist 应包含内置默认白名单 (登录/登录页/资源/接口文档)")
    void shouldIncludeDefaultWhitelist() {
        AuthClientProperties props = new AuthClientProperties();

        List<String> whitelist = props.getEffectiveWhitelist();

        assertThat(whitelist).isNotEmpty();
        // 默认白名单包含登录页、登录接口、API 文档相关
        assertThat(whitelist).anyMatch(s -> s.contains("login"));
        assertThat(whitelist).anyMatch(s -> s.contains("api-docs"));
        // 至少包含 /** 形式的兜底路径
        assertThat(whitelist).anyMatch(s -> s.contains("/**"));
    }

    @Test
    @DisplayName("addWhitelist: 动态添加的规则应出现在 effectiveWhitelist")
    void shouldAddDynamicWhitelist() {
        AuthClientProperties props = new AuthClientProperties();
        int baselineSize = props.getEffectiveWhitelist().size();

        props.addWhitelist("/v2/face/**");
        props.addWhitelist("/api/admin/**");

        List<String> whitelist = props.getEffectiveWhitelist();
        assertThat(whitelist).contains("/v2/face/**", "/api/admin/**");
        assertThat(whitelist.size()).isEqualTo(baselineSize + 2);
    }

    @Test
    @DisplayName("addWhitelist: null/blank 输入应忽略, 不抛出异常")
    void shouldIgnoreNullOrBlankInput() {
        AuthClientProperties props = new AuthClientProperties();
        int baselineSize = props.getEffectiveWhitelist().size();

        props.addWhitelist(null);
        props.addWhitelist("");
        props.addWhitelist("   ");

        assertThat(props.getEffectiveWhitelist().size()).isEqualTo(baselineSize);
    }

    @Test
    @DisplayName("addWhitelist: 自动 trim 首尾空白")
    void shouldTrimWhitespace() {
        AuthClientProperties props = new AuthClientProperties();

        props.addWhitelist("  /v2/face/**  ");

        assertThat(props.getEffectiveWhitelist()).contains("/v2/face/**");
    }

    @Test
    @DisplayName("removeWhitelist: 删除动态规则应从 effectiveWhitelist 移除")
    void shouldRemoveDynamicWhitelist() {
        AuthClientProperties props = new AuthClientProperties();
        props.addWhitelist("/v2/face/**");
        assertThat(props.getEffectiveWhitelist()).contains("/v2/face/**");

        props.removeWhitelist("/v2/face/**");

        assertThat(props.getEffectiveWhitelist()).doesNotContain("/v2/face/**");
    }

    @Test
    @DisplayName("removeWhitelist: 删除不存在的规则, 静默忽略")
    void shouldSilentlyIgnoreMissingRule() {
        AuthClientProperties props = new AuthClientProperties();
        int baselineSize = props.getEffectiveWhitelist().size();

        props.removeWhitelist("/never/added/**");

        assertThat(props.getEffectiveWhitelist().size()).isEqualTo(baselineSize);
    }

    @Test
    @DisplayName("clearDynamicWhitelist: 清空所有动态添加的规则, 保留默认白名单")
    void shouldClearDynamicWhitelist() {
        AuthClientProperties props = new AuthClientProperties();
        props.addWhitelist("/v2/face/**");
        props.addWhitelist("/api/x/**");
        assertThat(props.getEffectiveWhitelist()).contains("/v2/face/**", "/api/x/**");

        props.clearDynamicWhitelist();

        // 动态规则消失
        assertThat(props.getEffectiveWhitelist()).doesNotContain("/v2/face/**", "/api/x/**");
        // 默认白名单仍在
        assertThat(props.getEffectiveWhitelist()).isNotEmpty();
    }

    @Test
    @DisplayName("动态白名单线程安全: 并发 add/remove 一致")
    void shouldSupportConcurrentDynamicWhitelist() throws Exception {
        AuthClientProperties props = new AuthClientProperties();
        int threads = 8;
        int perThread = 100;

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int idx = t;
            workers[t] = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    props.addWhitelist("/rule-" + idx + "-" + i + "/**");
                }
            });
            workers[t].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        List<String> whitelist = props.getEffectiveWhitelist();
        // 至少添加了 threads * perThread 条规则
        long dynamicCount = whitelist.stream()
                .filter(s -> s.startsWith("/rule-"))
                .count();
        assertThat(dynamicCount).isEqualTo(threads * perThread);

        // 并发清空
        Thread clearer = new Thread(props::clearDynamicWhitelist);
        clearer.start();
        clearer.join();

        long afterClear = props.getEffectiveWhitelist().stream()
                .filter(s -> s.startsWith("/rule-"))
                .count();
        assertThat(afterClear).isZero();
    }

    @Test
    @DisplayName("getEffectiveWhitelist 应返回可变副本 (修改不影响内部状态)")
    void shouldReturnSnapshotList() {
        AuthClientProperties props = new AuthClientProperties();
        props.addWhitelist("/v2/face/**");

        List<String> snapshot = props.getEffectiveWhitelist();
        int snapshotSize = snapshot.size();
        snapshot.add("/malicious/**");

        // 重新获取的列表不应包含恶意添加
        List<String> fresh = props.getEffectiveWhitelist();
        assertThat(fresh).doesNotContain("/malicious/**");
        assertThat(fresh.size()).isEqualTo(snapshotSize);
    }
}