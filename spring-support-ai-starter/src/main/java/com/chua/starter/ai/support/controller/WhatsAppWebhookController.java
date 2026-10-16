package com.chua.starter.ai.support.controller;

import com.chua.common.support.annotation.Ignore;
import com.chua.starter.ai.support.service.AiBotChatService;
import com.chua.whatsapp.support.bot.WhatsAppBotClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * WhatsApp Cloud API Webhook 控制器。
 *
 * <p>Meta 侧回调地址填 {@code https://<domain>/v2/webhook/whatsapp}：
 * GET 用于订阅握手，POST 用于接收消息事件。该端点不参与登录鉴权，
 * 由 Meta App Secret 的 {@code X-Hub-Signature-256} 完成验签。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Ignore
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
     * @param body    原始请求体
     * @param request 请求（用于取签名头）
     * @return 转发给对应 WhatsApp 客户端后的结果
     */
    @PostMapping
    public ResponseEntity<String> callback(@RequestBody String body, HttpServletRequest request) {
        WhatsAppBotClient.WhatsAppCallbackResult result =
                aiBotChatService.handleWhatsAppWebhook(body, collectHeaders(request));
        return ResponseEntity.status(result.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.body());
    }

    /**
     * 请求头转小写 Map，供签名校验使用。
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