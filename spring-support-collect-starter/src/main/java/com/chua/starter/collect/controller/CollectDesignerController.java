package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.engine.executor.CollectAgentCatalog;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.service.CollectDesignerService;
import com.chua.starter.collect.vo.CollectOptionVO;
import com.chua.starter.collect.vo.CollectPreviewVO;
import com.chua.starter.collect.vo.CollectRuntimeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 采集设计器接口。
 *
 * <p>工具箱、属性面板、运行概览与定时预览的数据都来自这里，设计器不内置节点类型清单。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-设计器")
@RestController
@RequestMapping("/v2/collect/designer")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectDesignerController {

    /**
     * 设计器服务。
     */
    private final CollectDesignerService designerService;

    /**
     * Agent 目录；未集成 AI starter 时为空。
     */
    private final ObjectProvider<CollectAgentCatalog> agentCatalogProvider;

    /**
     * 全部节点类型元数据。
     *
     * @return 节点元数据列表
     */
    @Operation(summary = "节点类型元数据")
    @GetMapping("/metas")
    public ReturnResult<List<CollectShapeMeta>> metas() {
        return ReturnResult.ok(designerService.metas());
    }

    /**
     * 已注册 Agent 目录，供设计器刷新 Agent 节点下拉。
     *
     * @return Agent 选项；未集成 AI starter 时为空
     */
    @Operation(summary = "已注册 Agent 目录")
    @GetMapping("/agents")
    public ReturnResult<List<CollectOptionVO>> agents() {
        CollectAgentCatalog catalog = agentCatalogProvider.getIfAvailable();
        return ReturnResult.ok(catalog == null ? List.of() : catalog.options());
    }

    /**
     * 运行时概览。
     *
     * @return 概览出参
     */
    @Operation(summary = "运行时概览")
    @GetMapping("/runtime")
    public ReturnResult<CollectRuntimeVO> runtime() {
        return ReturnResult.ok(designerService.runtime());
    }

    /**
     * 预览定时表达式的触发时间。
     *
     * @param cron  定时表达式
     * @param times 预览次数
     * @return 触发时间列表
     */
    @Operation(summary = "预览定时触发时间")
    @GetMapping("/cron/next")
    public ReturnResult<List<LocalDateTime>> cronNext(@RequestParam String cron,
                                                      @RequestParam(defaultValue = "5") int times) {
        return ReturnResult.ok(designerService.cronPreview(cron, times));
    }

    /**
     * 预览抓取：把 URL 抓回来给「元素拾取器」渲染与点选。
     *
     * <p>设计期拿不到运行期的抓取变量，所以单开这个只读出口。仅允许 http/https，
     * 且拒绝内网与本机地址（见 {@code CollectDesignerServiceImpl#blockedHost}）。</p>
     *
     * <p>{@code render=true} 时用真实浏览器执行 JS 后取 DOM：SPA 站点在 HTTP 直取下
     * 通常只有一个空壳，点选器无从下手。渲染要启浏览器进程，单次开销在秒级，默认关闭。</p>
     *
     * @param url       目标地址
     * @param timeoutMs 超时毫秒，可空
     * @param render    是否用真实浏览器渲染后取 DOM，默认 false
     * @return 抓取结果（状态码、Content-Type、正文、是否渲染）
     */
    @Operation(summary = "预览抓取（元素拾取器用）")
    @GetMapping("/fetch-preview")
    public ReturnResult<CollectPreviewVO> fetchPreview(@RequestParam String url,
                                                       @RequestParam(required = false) Integer timeoutMs,
                                                       @RequestParam(required = false, defaultValue = "false")
                                                       boolean render) {
        return ReturnResult.ok(designerService.previewFetch(url, timeoutMs, render));
    }

    /**
     * AI 生成流程：把自然语言描述生成流程 DSL（经图校验后返回）。
     *
     * @param body 入参：{@code input} 为自然语言描述
     * @return 规范化后的流程 DSL
     */
    @Operation(summary = "AI 生成流程")
    @PostMapping("/ai-generate")
    public ReturnResult<CollectDslDTO> aiGenerate(@RequestBody Map<String, String> body) {
        return ReturnResult.ok(designerService.aiGenerate(body == null ? null : body.get("input")));
    }
}
