package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.service.MailboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 邮箱账号(MAILBOX 身份)邮件箱接口
 * <p>读取邮箱身份的 IMAP 收件箱：列表 + 邮件正文详情。邮箱地址/授权码均取自登录身份，授权码加密存储、不对外返回。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "服务器资产-邮箱账号", description = "MAILBOX 身份邮件箱(IMAP 读取)")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/identity")
@RequiredArgsConstructor
public class MailboxController {

    /** 邮箱读取服务 */
    private final MailboxService mailboxService;

    /**
     * 收件箱邮件列表
     *
     * @param identityId 邮箱身份 标识
     * @param folder     文件夹(默认 INBOX)
     * @param limit      最大条数(默认 20)
     * @return 邮件列表
     */
    @Operation(summary = "邮箱收件箱列表")
    @GetMapping("/{identityId}/mailbox/list")
    public ReturnResult<List<Map<String, Object>>> list(@PathVariable Long identityId,
                                                        @RequestParam(required = false) String folder,
                                                        @RequestParam(required = false, defaultValue = "20") int limit) {
        return mailboxService.list(identityId, folder, limit);
    }

    /**
     * 邮件正文详情
     *
     * @param identityId 邮箱身份 标识
     * @param folder     文件夹(默认 INBOX)
     * @param subject    邮件主题关键字(定位，可空默认最新)
     * @return 邮件详情
     */
    @Operation(summary = "邮件正文详情")
    @GetMapping("/{identityId}/mailbox/message")
    public ReturnResult<Map<String, Object>> detail(@PathVariable Long identityId,
                                                    @RequestParam(required = false) String folder,
                                                    @RequestParam(required = false) String subject) {
        return mailboxService.detail(identityId, folder, subject);
    }
}
