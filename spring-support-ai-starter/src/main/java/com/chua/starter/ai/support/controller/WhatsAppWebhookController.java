// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.common.support.annotation.Ignore;
import com.chua.starter.ai.support.service.AiBotChatService;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.whatsapp.support.bot.WhatsAppBotClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * WhatsApp Cloud API Webhook 控制器。
 *
 * <p>Meta 侧回调地址填 {@code https://<domain>/v2/webhook/whatsapp}：
 * GET 用于订阅握手，POST 用于接收消息事件。该端点不参与登录鉴权（{@link Ignore}），
 * 返回体不走统一信封（{@link ApiReturnFormatIgnore}），由 Meta App Secret 的
 * {@code X-Hub-Signature-256} 完成验签。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Ignore
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/v2/webhook/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final AiBotChatService aiBotChatService;

    /**
     * Meta Webhook 订阅握手。
     *
     * @param mode      hub.mode
     * @param token     hub.verify_token
     * @param challenge hub.challenge
     * @return 校验通过回显 challenge，否则 403
     */
    @GetMapping
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode") String mode,
                                         @RequestParam(name = "hub.verify_token") String token,
                                         @RequestParam(name = "hub.challenge") String challenge) {
        String result = aiBotChatService.verifyWhatsAppWebhook(mode, token, challenge);
        if (result == null) {
            log.warn("[WhatsApp] Webhook 握手校验失败 mode={}", mode);
            return ResponseEntity.status(403).body("forbidden");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Meta Webhook 事件回调。
     *
     * <p>直接读取原始请求体字节做验签与解析，避免 Jackson 反序列化改变字节内容导致
     * HMAC 校验失败。</p>
     *
     * @param request 请求
     * @return 解析结果对应的 HTTP 状态与响应体
     */
    @PostMapping
    public ResponseEntity<String> callback(HttpServletRequest request) {
        String body;
        try {
            body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[WhatsApp] 读取 Webhook 请求体失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"msg\":\"unreadable body\"}");
        }
        WhatsAppBotClient.WhatsAppCallbackResult result =
                aiBotChatService.handleWhatsAppWebhook(body, collectHeaders(request));
        return ResponseEntity.status(result.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.body());
    }

    /**
     * 请求头转 Map，供签名校验使用。
     *
     * @param request 请求
     * @return 请求头映射
     */
    private Map<String, String> collectHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}