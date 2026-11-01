package com.chua.starter.ngrok.support;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.common.support.tunnel.TunnelStatusStream;
import com.chua.starter.common.support.tunnel.TunnelView;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * ngrok 端口映射管理接口（{@code /ms/tunnel/ngrok}）。
 *
 * <p>页面用这一组接口完成「本机端口 → 公网 URL」的全生命周期管理：
 * 新建映射、保存到本地文件、启动 / 停止、删除。启动失败不会抛 500 空响应，
 * 而是把原因放进 {@code ReturnResult.msg}，页面直接展示。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Tag(name = "Ngrok 隧道", description = "本机端口通过 ngrok 映射到公网")
@RestController
@RequestMapping("/ms/tunnel/ngrok")
public class NgrokTunnelController {

    /**
     * 隧道编排服务。
     */
    private final NgrokTunnelService service;

    /**
     * 实时状态推送器。
     */
    private final TunnelStatusStream statusStream;

    /**
     * 创建控制器。
     *
     * @param service 隧道编排服务
     */
    public NgrokTunnelController(NgrokTunnelService service) {
        this.service = service;
        this.statusStream = new TunnelStatusStream(service::list, 1500L);
    }

    /**
     * 实时状态流（SSE）。
     *
     * <p>页面订阅后每 1.5 秒收到一份映射快照，启停结果无需手动刷新。</p>
     *
     * @return SSE 连接
     */
    @Operation(summary = "映射实时状态（SSE）")
    @GetMapping("/stream")
    public SseEmitter stream() {
        return statusStream.subscribe();
    }

    /**
     * 关闭推送线程。
     */
    @PreDestroy
    public void destroy() {
        statusStream.close();
    }

    /**
     * 模块能力（凭据是否配置、持久化目录）。
     *
     * @return 能力信息
     */
    @Operation(summary = "ngrok 能力探测")
    @GetMapping("/capabilities")
    public ReturnResult<Map<String, Object>> capabilities() {
        return ReturnResult.ok(service.capabilities());
    }

    /**
     * 映射列表（含运行态）。
     *
     * @return 映射列表
     */
    @Operation(summary = "映射列表")
    @GetMapping
    public ReturnResult<List<TunnelView>> list() {
        return ReturnResult.ok(service.list());
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
     * @param request 配置
     * @return 保存后的映射
     */
    @Operation(summary = "新建映射")
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
}
