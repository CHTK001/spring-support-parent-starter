// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Gemini 协议「模型名在路径上」的解析测试。
 *
 * <p>为什么单测这条：Gemini 的模型名<b>不在请求体里</b>，而在
 * {@code /v1beta/models/{model}:generateContent} 路径上。历史实现只从 body 取，
 * 取到 null 之后模型范围校验把 null 当越权 —— 结果是「建了带限定模型的 Key，
 * 调 Gemini 一律 403」，且底层请求也拿不到模型名。这个解析器错一格就是整条协议不可用。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@DisplayName("AiProtocolController 模型名路径解析")
class AiProtocolModelPathTest {

    @Test
    @DisplayName("标准 Gemini 路径带动作后缀")
    void geminiWithAction() {
        assertEquals("gemini-1.5-pro",
                AiProtocolController.modelFromPath("/v1beta/models/gemini-1.5-pro:generateContent"));
        assertEquals("gemini-1.5-pro",
                AiProtocolController.modelFromPath("/v1beta/models/gemini-1.5-pro:streamGenerateContent"));
    }

    @Test
    @DisplayName("无动作后缀 / 带查询参数 / 带尾斜杠")
    void variations() {
        assertEquals("gemini-pro", AiProtocolController.modelFromPath("/v1beta/models/gemini-pro"));
        assertEquals("gemini-pro", AiProtocolController.modelFromPath("/v1beta/models/gemini-pro?key=abc"));
        assertEquals("gemini-pro", AiProtocolController.modelFromPath("/v1beta/models/gemini-pro/"));
    }

    @Test
    @DisplayName("带版本前缀（models/ 之前还有路径）")
    void nestedPrefix() {
        assertEquals("gemini-2-flash",
                AiProtocolController.modelFromPath("/api/v1beta/models/gemini-2-flash:generateContent"));
    }

    @Test
    @DisplayName("OpenAI 风格路径没有 /models/ 段 → null（不由路径兜底）")
    void openAiPath() {
        assertNull(AiProtocolController.modelFromPath("/v1/chat/completions"));
        assertNull(AiProtocolController.modelFromPath("/v1beta/models"));
        assertNull(AiProtocolController.modelFromPath("/v2/v1/models"));
    }

    @Test
    @DisplayName("空模型段 / null / 空串 → null")
    void degenerate() {
        assertNull(AiProtocolController.modelFromPath("/v1beta/models/:generateContent"));
        assertNull(AiProtocolController.modelFromPath("/v1beta/models/"));
        assertNull(AiProtocolController.modelFromPath((String) null));
        assertNull(AiProtocolController.modelFromPath(""));
        assertNull(AiProtocolController.modelFromPath((jakarta.servlet.http.HttpServletRequest) null));
    }
}
