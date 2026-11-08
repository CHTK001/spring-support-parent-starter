package com.chua.starter.account.mail;

import com.chua.starter.account.common.ApiResult;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 收 / 发信箱接口。
 *
 * <p>挂在统一账号下面：账号页右键「打开邮箱 / 打开短信」进入的就是这里的收信箱，
 * 不跳外链、不依赖第三方 Web 控制台。</p>
 *
 * <ul>
 *   <li>{@code GET /api/accounts/{accountId}/mailbox}：能否收 / 发信与可用文件夹；</li>
 *   <li>{@code GET /api/accounts/{accountId}/mailbox/messages}：某个文件夹的信件列表（不含正文）；</li>
 *   <li>{@code GET /api/accounts/{accountId}/mailbox/messages/{messageId}}：单封详情（含正文）；</li>
 *   <li>{@code POST /api/accounts/{accountId}/mailbox/send}：发信（邮件 / 短信统一入口）。</li>
 * </ul>
 *
 * <p>凭据只在服务端解密后交给收信 SPI，本接口不返回、不接受明文密码。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@ApiReturnFormatIgnore
@RestController("accountMailboxController")
@RequestMapping("/api/accounts/{accountId}/mailbox")
@RequiredArgsConstructor
public class MailboxController {

    /**
     * 收 / 发信箱编排服务
     */
    private final MailboxService mailboxService;

    /**
     * 信箱概览。
     *
     * @param accountId 账号标识
     * @return 概览（含可用文件夹与不可用原因）
     */
    @GetMapping
    public ApiResult<MailboxOverview> overview(@PathVariable("accountId") String accountId) {
        return ApiResult.ok(mailboxService.overview(accountId));
    }

    /**
     * 取信列表。
     *
     * @param accountId 账号标识
     * @param folder    服务端文件夹名（默认收件箱）
     * @param limit     取多少封（默认 20）
     * @param offset    跳过多少封
     * @param unreadOnly 只看未读
     * @param keyword   主题 / 发件人模糊词
     * @return 消息列表（不含正文，只带摘要）
     */
    @GetMapping("/messages")
    public ApiResult<List<MailboxMessage>> messages(
            @PathVariable("accountId") String accountId,
            @RequestParam(value = "folder", required = false) String folder,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset,
            @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly,
            @RequestParam(value = "keyword", required = false) String keyword) {
        MailboxQuery query = new MailboxQuery();
        if (folder != null) {
            query.setFolder(folder);
        }
        query.setLimit(limit);
        query.setOffset(offset);
        query.setUnreadOnly(unreadOnly);
        query.setKeyword(keyword);
        return ApiResult.ok(mailboxService.messages(accountId, query));
    }

    /**
     * 单封邮件详情。
     *
     * @param accountId 账号标识
     * @param messageId 消息标识
     * @param folder    服务端文件夹名（默认收件箱）
     * @return 消息详情（含正文与附件名）
     */
    @GetMapping("/messages/{messageId}")
    public ApiResult<MailboxMessage> message(
            @PathVariable("accountId") String accountId,
            @PathVariable("messageId") String messageId,
            @RequestParam(value = "folder", required = false) String folder) {
        return ApiResult.ok(mailboxService.message(accountId, folder, messageId));
    }

    /**
     * 发信。
     *
     * @param accountId 账号标识
     * @param request   发信内容
     * @return 发送结果（success / provider / messageId / message）
     */
    @PostMapping("/send")
    public ApiResult<Map<String, Object>> send(
            @PathVariable("accountId") String accountId,
            @RequestBody MailboxSendRequest request) {
        return ApiResult.ok(mailboxService.send(accountId, request));
    }
}
