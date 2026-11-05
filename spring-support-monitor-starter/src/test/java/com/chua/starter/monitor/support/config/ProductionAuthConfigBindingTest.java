// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.config;

import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产鉴权配置<b>真的绑得上</b>。
 *
 * <p>这条链路上有一个不查就会踩的坑：{@code AuthClientProperties.TempUser}
 * 内部类<b>没有 {@code @Data}</b>，也就是没有 {@code user} 的 setter。
 * Spring Boot 的 {@code JavaBeanBinder} 会退化为字段直接赋值，所以配置能绑进去；
 * 但这是「碰巧能行」，不是「设计上支持」。一旦有人给该类加校验、换成构造器绑定、
 * 或者把字段改名，这条就会无声断掉。</p>
 *
 * <p>无声断掉的后果特别难查：绑定失败不会让启动报错（{@code ignoreInvalidFields=true}
 * 就写在 {@code @ConfigurationProperties} 上），服务照常起来、配置面板上也写着
 * 「已启用鉴权」，但没有任何凭据能通过校验 —— 直到真实用户登录失败。
 * 守卫能发现「没有账号」，但发现不了「配了账号却根本没绑进来」，
 * 所以这条必须由测试钉住。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("生产鉴权配置绑定")
class ProductionAuthConfigBindingTest {

    /**
     * 用一份原始配置 map 走真实的 Binder。
     *
     * @param raw 配置项
     * @return 绑定后的属性对象
     */
    private static AuthClientProperties bind(Map<String, Object> raw) {
        return new Binder(new MapConfigurationPropertySource(raw))
                .bind("plugin.oauth", AuthClientProperties.class)
                .orElseGet(AuthClientProperties::new);
    }

    /**
     * 构造一份「该有的都配齐了」的原始配置。
     *
     * @param user 静态账号值
     * @return 原始配置
     */
    private static Map<String, Object> prodConfig(String user) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("plugin.oauth.enable", true);
        raw.put("plugin.oauth.protocol", "static");
        raw.put("plugin.oauth.temp.user", user);
        raw.put("plugin.oauth.static-token-expire-seconds", 7200);
        return raw;
    }

    @Test
    @DisplayName("plugin.oauth.temp.user 必须能真正绑进 TempUser（该类没有 setter，靠字段兜底）")
    void staticUserBindsWithoutSetter() {
        AuthClientProperties props = bind(prodConfig("admin:secret123"));

        assertNotNull(props.getTemp(), "temp 子对象必须存在");
        assertEquals("admin:secret123", props.getTemp().getUser(),
                "静态账号绑不进来 —— 守卫只会报「没有账号」，"
                        + "报不出「配了但没绑上」，这类故障会一路藏到用户登录失败");

        // 该值必须能被守卫认成「已配置」，否则守卫会误拒启动
        assertTrue(productionAuthGuardAccepts(props),
                "绑上来的账号必须让守卫判定为可用，否则生产会以「static 无账号」为由拒绝启动");
    }

    @Test
    @DisplayName("生产配置的其余关键项也要绑得上（enable / protocol / token 有效期）")
    void otherProdSettingsBind() {
        AuthClientProperties props = bind(prodConfig("admin:secret123"));

        assertTrue(props.isEnable(), "enable 必须为 true");
        assertEquals("static", props.getProtocol(), "protocol 必须为 static");
        assertEquals(7200L, props.getStaticTokenExpireSeconds(), "token 有效期应为 2h");
        assertFalse(props.isLocalAdminFallback(), "local-admin-fallback 必须为 false");
    }

    @Test
    @DisplayName("账号留空时守卫必须拒绝启动（此时配的是鉴权，只是没人能登录）")
    void blankAccountIsRejectedByGuard() {
        for (String blank : new String[]{null, "", "   "}) {
            AuthClientProperties props = bind(prodConfig(blank));
            assertFalse(productionAuthGuardAccepts(props),
                    "账号='" + blank + "' 时守卫必须拒绝启动");
        }
    }

    @Test
    @DisplayName("只绑 enable 而没绑 protocol 时，守卫不该误判（此时走默认 tcp，凭据在远端）")
    void defaultTcpProtocolNotRejected() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("plugin.oauth.enable", true);
        AuthClientProperties props = bind(raw);

        assertEquals("tcp", props.getProtocol(), "未显式配置时应为默认 tcp");
        assertTrue(productionAuthGuardAccepts(props),
                "tcp 的凭据由远端提供，本进程查不到账号不等于没有，拒绝启动属于误报");
    }

    /**
     * 让真实的守卫跑一遍启动期判定。
     *
     * <p>刻意走 {@code ProductionAuthGuard.afterPropertiesSet()} 而不是直接调纯函数：
     * 纯函数测试无法覆盖「守卫有没有把 temp 取出来接到判定上」，
     * 而那正是最容易漏接的一环。</p>
     *
     * @param props 绑定后的配置
     * @return 守卫是否接受该配置
     */
    private static boolean productionAuthGuardAccepts(AuthClientProperties props) {
        try {
            new ProductionAuthGuard(props).afterPropertiesSet();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }}