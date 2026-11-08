package com.chua.starter.channel.tempaccount;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.channel.ChannelRegistry;
import com.chua.starter.channel.spi.ChannelKind;
import com.chua.starter.channel.spi.TemporaryAccountProvider;
import com.chua.starter.channel.tempaccount.model.TempIdentity;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时账号 —— 随机虚拟身份的创建、查询与删除，及消息实时推送。
 *
 * <p>每个临时身份随机生成一个虚拟号码（接码）+ 一个临时邮箱（收信），
 * 号码收到的短信与邮箱收到的邮件由 {@link TempAccountService} 定时轮询，
 * 通过 {@code GET /stream}（SSE）实时推送到前端。</p>
 *
 * <p>本控制器为「渠道管理」下的临时账号子域（原属 monitor，已收敛至
 * {@code /api/channels/temp-accounts}）：渠道种类 {@code SMS} / {@code EMAIL}
 * 由 {@code com.chua.starter.channel.spi.TemporaryAccountProvider} 提供。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@ApiReturnFormatIgnore
@Slf4j
@Tag(name = "渠道-临时账号", description = "随机虚拟身份的创建 / 查询 / 删除与短信邮件实时推送")
@RestController
@RequestMapping("/api/channels/temp-accounts")
@RequiredArgsConstructor
public class TempAccountController {

    /**
     * 临时账号服务
     */
    private final TempAccountService tempAccountService;

    /**
     * 消息 SSE 注册表
     */
    private final TempAccountStreamRegistry streamRegistry;

    /**
     * 查询全部临时账号（列表轻量视图，含短信/邮件条数与号码/邮箱画像）。
     *
     * @return 账号列表
     */
    @Operation(summary = "查询全部临时账号")
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(tempAccountService.list());
    }

    /**
     * 查询单个临时账号详情（含全部短信与邮件）。
     *
     * @param id 账号标识
     * @return 账号详情；不存在返回空
     */
    @Operation(summary = "查询临时账号详情")
    @GetMapping("/{id}")
    public ApiResult<TempIdentity> detail(@PathVariable String id) {
        return ApiResult.ok(tempAccountService.detail(id));
    }

    /**
     * 随机创建一个临时账号。
     *
     * @param type         通道类型：sms（仅号码）/ email（仅邮箱）/ both（默认，号码 + 邮箱）
     * @param smsChannel   期望的短信渠道（可空，走优先级降级）
     * @param emailChannel 期望的邮件渠道（可空，走优先级降级）
     * @return 新账号
     */
    @Operation(summary = "随机创建临时账号",
            description = "type=sms|email|both（默认 both）；smsChannel/emailChannel 指定渠道，缺省取优先级最高者")
    @PostMapping
    public ApiResult<TempIdentity> create(@RequestParam(required = false) String type,
            @RequestParam(required = false) String smsChannel,
            @RequestParam(required = false) String emailChannel) {
        return ApiResult.ok(tempAccountService.create(type, smsChannel, emailChannel));
    }

    /**
     * 查询可用于临时身份的短信 / 邮件渠道（含进入优先级与默认标记）。
     *
     * <p>前端创建临时账号时用该接口生成渠道下拉：列表按 {@code priority} 降序，
     * {@code default} 标记为当前缺省渠道（优先级最高者）。</p>
     *
     * @return {@code {sms: [...], email: [...]}}
     */
    @Operation(summary = "查询临时身份可用渠道", description = "按进入优先级降序，default=true 为缺省渠道")
    @GetMapping("/available-channels")
    public ApiResult<Map<String, Object>> availableChannels() {
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("sms", describeTemporary(ChannelKind.SMS));
        payload.put("email", describeTemporary(ChannelKind.EMAIL));
        return ApiResult.ok(payload);
    }

    /**
     * 描述某一类临时渠道的可用实现。
     *
     * @param kind 渠道种类
     * @return 渠道描述列表（priority 降序，首项 default=true）
     */
    private List<Map<String, Object>> describeTemporary(ChannelKind kind) {
        List<TemporaryAccountProvider> providers = ChannelRegistry.temporaryOf(kind);
        List<Map<String, Object>> result = new ArrayList<>(providers.size());
        for (int i = 0; i < providers.size(); i++) {
            TemporaryAccountProvider provider = providers.get(i);
            Map<String, Object> item = new LinkedHashMap<>(5);
            item.put("channel", provider.channel());
            item.put("displayName", provider.displayName());
            item.put("priority", provider.priority());
            item.put("default", i == 0);
            result.add(item);
        }
        return result;
    }

    /**
     * 删除临时账号。
     *
     * @param id 账号标识
     * @return 删除结果
     */
    @Operation(summary = "删除临时账号")
    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable String id) {
        return ApiResult.ok(tempAccountService.delete(id));
    }

    /**
     * 订阅临时账号消息流（SSE）。
     *
     * <p>事件：{@code ready}（建连就绪）、{@code account}（账号新增）、
     * {@code message}（新短信/新邮件）、{@code remove}（账号删除）。</p>
     *
     * @return SSE 长连接
     */
    @Operation(summary = "订阅临时账号消息流（SSE）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streamRegistry.subscribe();
    }

    /**
     * 订阅连接数（诊断用）。
     *
     * @return 连接数
     */
    @Operation(summary = "SSE 订阅连接数", hidden = true)
    @GetMapping("/stream/count")
    public ApiResult<Map<String, Integer>> streamCount() {
        return ApiResult.ok(Map.of("count", streamRegistry.subscriberCount()));
    }
}
