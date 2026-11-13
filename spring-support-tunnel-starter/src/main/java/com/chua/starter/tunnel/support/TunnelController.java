package com.chua.starter.tunnel.support;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.common.support.tunnel.TunnelStatusStream;
import com.chua.starter.common.support.tunnel.TunnelView;
import com.chua.starter.tunnel.properties.TunnelProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 内网穿透统一接口（{@code /ms/tunnel}）——所有提供方共用这一套路由。
 *
 * <p>改造前是「一个提供方一个入口」：{@code /ms/tunnel/ngrok}、
 * {@code /ms/tunnel/cloudflared}，两个控制器 97% 的代码重复，页面也要为每个
 * 提供方维护一套 API。现在合并为单一入口：</p>
 * <ul>
 *   <li>{@code GET /ms/tunnel/providers} 列出全部提供方及其能力；</li>
 *   <li>{@code GET /ms/tunnel?provider=xxx} 列出映射（不带参数即全部提供方）；</li>
 *   <li>其余接口按映射 id 操作，不再需要 provider 前缀——id 全局唯一。</li>
 * </ul>
 *
 * <p>因此新增一个提供方只需实现 {@code TunnelProvider} 并注册为 Bean，
 * 本控制器与页面都不必改动。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Tag(name = "内网穿透", description = "本机端口映射到公网，支持多种提供方（ngrok / Cloudflare Tunnel）")
@RestController
@RequestMapping("/ms/tunnel")
public class TunnelController {

    /**
     * 编排服务。
     */
    private final TunnelService service;

    /**
     * 实时状态推送器（仅 Web 层持有）。
     */
    private final TunnelStatusStream statusStream;

    /**
     * 创建控制器。
     *
     * @param service    编排服务
     * @param properties 统一配置（推送间隔）
     */
    public TunnelController(TunnelService service, TunnelProperties properties) {
        this.service = service;
        long interval = properties.getSseInterval() == null
                // [P3C A20 豁免]
                ? 1500L : properties.getSseInterval().toMillis();
        this.statusStream = new TunnelStatusStream(service::listAll, interval);
    }

    /**
     * 提供方列表（含能力、支持模式、可用性）。
     *
     * @return 提供方描述
     */
    @Operation(summary = "提供方列表", description = "页面据此渲染提供方下拉与模式选项")
    @GetMapping("/providers")
    public ReturnResult<List<Map<String, Object>>> providers() {
        return ReturnResult.ok(service.providers());
    }

    /**
     * 映射列表（含运行态）。
     *
     * @param provider 仅该提供方；不传返回全部
     * @return 映射列表
     */
    @Operation(summary = "映射列表")
    @GetMapping
    public ReturnResult<List<TunnelView>> list(
            @RequestParam(value = "provider", required = false) String provider) {
        return ReturnResult.ok(service.list(provider));
    }

    /**
     * 实时状态流（SSE）。
     *
     * @return SSE 连接
     */
    @Operation(summary = "映射实时状态（SSE）")
    @GetMapping("/stream")
    public SseEmitter stream() {
        return statusStream.subscribe();
    }

    /**
     * 单条映射。
     *
     * @param id 标识
     * @return 映射
     */
    @Operation(summary = "映射详情")
    @GetMapping("/{id}")
    public ReturnResult<TunnelView> detail(@PathVariable String id) {
        try {
            return ReturnResult.ok(service.get(id));
        } catch (RuntimeException e) {
            return ReturnResult.error(e, "映射不存在");
        }
    }

    /**
     * 新建映射。
     *
     * <p>请求体里用 {@code provider} 指定提供方（如 {@code ngrok} /
     * {@code cloudflared}）；系统只注册了一个提供方时可省略。</p>
     *
     * @param request 配置
     * @return 保存后的映射
     */
    @Operation(summary = "新建映射", description = "请求体的 provider 字段决定由哪个提供方承载")
    @PostMapping
    public ReturnResult<TunnelView> create(@RequestBody TunnelRecord request) {
        try {
            return ReturnResult.ok(service.create(request));
        } catch (RuntimeException e) {
            return ReturnResult.error(e, "新建映射失败");
        }
    }

    /**
     * 更新映射（运行中会按新配置重启）。
     *
     * @param id      标识
     * @param request 新配置
     * @return 保存后的映射
     */
    @Operation(summary = "更新映射")
    @PutMapping("/{id}")
    public ReturnResult<TunnelView> update(@PathVariable String id, @RequestBody TunnelRecord request) {
        try {
            return ReturnResult.ok(service.update(id, request));
        } catch (RuntimeException e) {
            return ReturnResult.error(e, "更新映射失败");
        }
    }

    /**
     * 删除映射。
     *
     * @param id 标识
     * @return 是否删除
     */
    @Operation(summary = "删除映射")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable String id) {
        try {
            return ReturnResult.ok(service.delete(id));
        } catch (RuntimeException e) {
            return ReturnResult.error(e, "删除映射失败");
        }
    }

    /**
     * 启动映射。
     *
     * @param id 标识
     * @return 启动后的映射
     */
    @Operation(summary = "启动映射")
    @PostMapping("/{id}/start")
    public ReturnResult<TunnelView> start(@PathVariable String id) {
        try {
            return ReturnResult.ok(service.start(id));
        } catch (RuntimeException e) {
            return ReturnResult.error(e, "启动映射失败");
        }
    }

    /**
     * 停止映射。
     *
     * @param id 标识
     * @return 停止后的映射
     */
    @Operation(summary = "停止映射")
    @PostMapping("/{id}/stop")
    public ReturnResult<TunnelView> stop(@PathVariable String id) {
        try {
            return ReturnResult.ok(service.stop(id));
        } catch (RuntimeException e) {
            return ReturnResult.error(e, "停止映射失败");
        }
    }

    /**
     * 关闭推送线程。
     */
    @PreDestroy
    public void destroy() {
        statusStream.close();
    }
}
