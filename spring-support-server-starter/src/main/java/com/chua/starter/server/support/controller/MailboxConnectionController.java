package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.datasearch.email.spi.EmailProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.server.support.service.MailboxSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 邮箱账号(MAILBOX) SSE 连接与临时邮箱管理
 * <p>提供邮箱推送连接注册/注销、临时邮箱地址创建。前端通过 /sse/connect 建立 SSE 连接后注册到本控制器。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "服务器资产-邮箱账号SSE", description = "MAILBOX 身份邮件箱推送与临时邮箱")
@RestController
@RequestMapping("/ms/server/identity")
@RequiredArgsConstructor
@Slf4j
public class MailboxConnectionController {

    /**
     * 邮箱 SSE 推送服务
    */
    private final MailboxSseService mailboxSseService;

    /**
     * 注册邮箱推送(前端打开邮件箱时调用)。
     *
     * @param identityId 邮箱身份 标识
     * @param clientId  前端 SSE 连接 客户端标识
     * @return 结果
     */
    @Operation(summary = "注册邮箱推送连接")
    @PostMapping("/{identityId}/mailbox/register")
    public ReturnResult<Void> register(@PathVariable Long identityId, @RequestParam String clientId) {
        mailboxSseService.register(clientId, identityId);
        return ReturnResult.success();
    }

    /**
     * 注销邮箱推送(前端关闭邮件箱时调用)。
     *
     * @param clientId 前端 SSE 连接 客户端标识
     * @return 结果
     */
    @Operation(summary = "注销邮箱推送连接")
    @DeleteMapping("/mailbox/unregister")
    public ReturnResult<Void> unregister(@RequestParam String clientId) {
        mailboxSseService.unregister(clientId);
        return ReturnResult.success();
    }

    /**
     * 创建临时邮箱地址(TEMP 来源)。
     *
     * @param provider 服务商名(fce/luckmail/mailnest/mailtm)
     * @return 临时邮箱地址
     */
    @Operation(summary = "创建临时邮箱地址")
    @PostMapping("/mailbox/temp-create")
    public ReturnResult<Map<String, String>> createTempEmail(@RequestParam String provider) {
        if (provider == null || provider.isBlank()) {
            return ReturnResult.error("临时邮箱服务商不能为空");
        }
        try {
            EmailProvider emailProvider = ServiceProvider.of(EmailProvider.class).getExtension(provider);
            if (emailProvider == null) {
                return ReturnResult.error("未找到临时邮箱服务商: " + provider);
            }
            String email = emailProvider.createEmail();
            if (email == null || email.isBlank()) {
                return ReturnResult.error("创建临时邮箱失败(请检查服务商配置/网络)");
            }
            return ReturnResult.ok(Map.of("email", email, "provider", provider));
        } catch (Exception e) {
            log.warn("[mailbox] 创建临时邮箱失败 provider={} - {}", provider, e.getMessage());
            return ReturnResult.error("创建临时邮箱失败: " + e.getMessage());
        }
    }
}
