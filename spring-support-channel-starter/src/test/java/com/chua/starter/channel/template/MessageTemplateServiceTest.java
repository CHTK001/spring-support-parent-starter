package com.chua.starter.channel.template;

import com.chua.starter.ai.support.manage.common.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MessageTemplateService} 纯渲染逻辑回归测试。
 *
 * <p>渲染不依赖数据库：直接构造服务（mapper 传 {@code null}），只验证：</p>
 * <ul>
 *   <li>{@code {{变量}}} 占位替换与 {@code missing} 收集；</li>
 *   <li>内置时间变量自动补齐；</li>
 *   <li>短信 / 邮件两种种类的变量清单差异；</li>
 *   <li>非法种类被拒绝。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
class MessageTemplateServiceTest {

    /**
     * 被测服务（渲染路径不触库，mapper 置空即可）
     */
    private final MessageTemplateService service = new MessageTemplateService(null);

    @Test
    @DisplayName("render：已提供变量被替换，未提供变量保留占位并记入 missing")
    void shouldRenderVariablesAndCollectMissing() {
        Map<String, Object> variables = new LinkedHashMap<>(2);
        variables.put("code", "123456");
        variables.put("from", "10086");

        Map<String, Object> rendered = service.render("SMS", null,
                "验证码：{{code}}，来自 {{from}}，时间 {{time}}，未知 {{unknown}}", variables);

        assertInstanceOf(String.class, rendered.get("content"));
        String content = (String) rendered.get("content");
        assertTrue(content.contains("验证码：123456"));
        assertTrue(content.contains("来自 10086"));
        assertTrue(content.contains("{{unknown}}"), "未提供的变量应保留占位");
        assertEquals("[unknown]", String.valueOf(rendered.get("missing")));
    }

    @Test
    @DisplayName("render：邮件主题同样支持变量替换")
    void shouldRenderEmailSubject() {
        Map<String, Object> variables = new LinkedHashMap<>(1);
        variables.put("subject", "登录验证码");

        Map<String, Object> rendered = service.render("EMAIL", "{{subject}} - 系统通知",
                "验证码 {{code}}", variables);

        assertEquals("登录验证码 - 系统通知", rendered.get("subject"));
        assertTrue(String.valueOf(rendered.get("missing")).contains("code"));
    }

    @Test
    @DisplayName("render：变量占位允许 {{ name }} 形式的前后空格")
    void shouldSupportWhitespaceInsidePlaceholder() {
        Map<String, Object> variables = new LinkedHashMap<>(1);
        variables.put("code", "4321");

        String content = (String) service.render("SMS", null, "验证码 {{  code  }}", variables)
                .get("content");
        assertEquals("验证码 4321", content);
    }

    @Test
    @DisplayName("variablesOf：短信与邮件的内置变量清单不同")
    void shouldExposeKindSpecificVariables() {
        assertTrue(service.variablesOf("SMS").contains("content"));
        assertTrue(service.variablesOf("EMAIL").contains("body"));
    }

    @Test
    @DisplayName("render：非法种类直接拒绝")
    void shouldRejectUnknownKind() {
        assertThrows(ApiException.class,
                () -> service.render("WECHAT", null, "{{code}}", Map.of()));
    }
}
