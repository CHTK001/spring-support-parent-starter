package com.chua.starter.account.sendlog;

import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.sendlog.store.SysAccountSendLog;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 统一发送日志管理接口。
 *
 * <p>给「发送日志」管理页用：邮件 / 短信每次真实发送（成功或失败）都会在
 * {@code MailboxService} 发信门面与 system 消息推送侧异步落一条通道级日志，
 * 这里提供分页查询与按时间清理。</p>
 *
 * <ul>
 *   <li>{@code GET /api/accounts/send-logs}：分页查询（种类 / 结果 / 账号 / 收件人 / 关键字 / 时间范围）；</li>
 *   <li>{@code DELETE /api/accounts/send-logs}：清理指定时间之前的日志。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.59
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts/send-logs")
@RequiredArgsConstructor
public class SendLogController {

    /**
     * 发送日志服务
     */
    private final SendLogService sendLogService;

    /**
     * 分页查询发送日志。
     *
     * @param kind      账号种类（EMAIL / SMS，可空）
     * @param success   发送结果（可空表示全部）
     * @param accountId 发信账号 ID（可空）
     * @param source    调用来源（可空）
     * @param target    收件人模糊匹配（可空）
     * @param keyword   关键字（可空）
     * @param startTime 发送时间下界（可空）
     * @param endTime   发送时间上界（可空）
     * @param page      页码（默认 1）
     * @param pageSize  每页条数（默认 20，上限 200）
     * @return 分页结果
     */
    @GetMapping
    public ApiResult<SendLogPage> page(
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "target", required = false) String target,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        SendLogQuery query = new SendLogQuery(kind, success, accountId, source,
                target, keyword, startTime, endTime, page, pageSize);
        return ApiResult.ok(sendLogService.page(query));
    }

    /**
     * 清理指定时间之前的发送日志。
     *
     * @param before 截止时间（必填，防止误清全表）
     * @return 删除条数
     */
    @DeleteMapping
    public ApiResult<Integer> clear(
            @RequestParam("before") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before) {
        return ApiResult.ok(sendLogService.clearBefore(before));
    }
}
