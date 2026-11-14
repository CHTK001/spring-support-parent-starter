package com.chua.starter.account.mail;

import com.chua.starter.account.common.ApiResult;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 短信模板同步接口。
 *
 * <p>按账号 vendor 解析 MessagePush SPI 的 {@code listTemplates()}，把取回的模板
 * 落到消息模板表（kind=SMS）；不依赖具体厂商。是否真的来自厂商云端取决于通道实现：
 * 阿里云查审核通过的云端模板，腾讯 / 百度当前只返回本进程已登记模板。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class SmsTemplateSyncController {

    /**
     * 短信模板同步服务
     */
    private final SmsTemplateSyncService smsTemplateSyncService;

    /**
     * 同步指定短信账号的模板（云端 / 本地已登记，取决于通道实现）。
     *
     * @param accountId 账号标识
     * @return {@code {provider, fetched, created, updated, skipped}}
     */
    @PostMapping("/{accountId}/sms/templates/sync")
    public ApiResult<Map<String, Object>> sync(@PathVariable("accountId") String accountId) {
        return ApiResult.ok(smsTemplateSyncService.sync(accountId));
    }
}
