package com.chua.starter.monitor.support.tempaccount;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.tempaccount.model.TempAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 临时账号 —— 随机虚拟身份的创建、查询与删除，及消息实时推送。
 *
 * <p>每个临时账号随机生成一个虚拟号码（接码）+ 一个临时邮箱，
 * 号码收到的短信与邮箱收到的邮件由 {@link TempAccountService} 定时轮询，
 * 通过 {@code GET /stream}（SSE）实时推送到前端抽屉。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控-临时账号", description = "随机虚拟身份的创建 / 查询 / 删除与短信邮件实时推送")
@RestController
@RequestMapping("/ms/monitor/temp-account")
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
    @GetMapping("/list")
    public ReturnResult<List<Map<String, Object>>> list() {
        return ReturnResult.ok(tempAccountService.list());
    }

    /**
     * 查询单个临时账号详情（含全部短信与邮件）。
     *
     * @param id 账号标识
     * @return 账号详情；不存在返回空
     */
    @Operation(summary = "查询临时账号详情")
    @GetMapping("/{id}")
    public ReturnResult<TempAccount> detail(@PathVariable String id) {
        return ReturnResult.ok(tempAccountService.detail(id));
    }

    /**
     * 随机创建一个临时账号。
     *
     * @param type 通道类型：sms（仅号码）/ email（仅邮箱）/ both（默认，号码 + 邮箱）
     * @return 新账号
     */
    @Operation(summary = "随机创建临时账号", description = "type=sms|email|both（默认 both）随机生成对应通道")
    @PostMapping("/create")
    public ReturnResult<TempAccount> create(@RequestParam(required = false) String type) {
        return ReturnResult.ok(tempAccountService.create(type));
    }

    /**
     * 删除临时账号。
     *
     * @param id 账号标识
     * @return 删除结果
     */
    @Operation(summary = "删除临时账号")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable String id) {
        return ReturnResult.ok(tempAccountService.delete(id));
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
    public ResponseEntity<SseEmitter> stream() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(streamRegistry.subscribe());
    }

    /**
     * 订阅连接数（诊断用）。
     *
     * @return 连接数
     */
    @Operation(summary = "SSE 订阅连接数", hidden = true)
    @GetMapping("/stream/count")
    public ReturnResult<Map<String, Integer>> streamCount() {
        return ReturnResult.ok(Map.of("count", streamRegistry.subscriberCount()));
    }
}