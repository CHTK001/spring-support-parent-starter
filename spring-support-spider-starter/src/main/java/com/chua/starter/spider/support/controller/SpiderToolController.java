package com.chua.starter.spider.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.spider.support.SpiderToolkit;
import com.chua.spider.support.model.SpiderPreviewResult;
import com.chua.spider.support.model.SpiderSelectorType;
import com.chua.spider.support.model.SpiderTaskDefinition;
import com.chua.starter.spider.support.spi.SpiderNodeExecutorRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 爬虫工具接口。
 *
 * <p>提供页面预览和选择器测试功能，供前端编排工作台调试使用。</p>
 *
 * @author CH
 */
@RestController
@RequestMapping("/v1/spider")
@RequiredArgsConstructor
public class SpiderToolController {

    private final SpiderToolkit spiderToolkit;

    @Autowired(required = false)
    private SpiderNodeExecutorRegistry nodeExecutorRegistry;

    // ── B47 POST /v1/spider/preview ───────────────────────────────────────────

    /**
     * POST /v1/spider/preview — 预览页面 HTML。
     *
     * <p>调用 {@link SpiderToolkit#preview(String, SpiderTaskDefinition)} 抓取目标页面，
     * 返回 HTML 内容、标题和状态码。</p>
     */
    @PostMapping("/preview")
    public ReturnResult<SpiderPreviewResult> preview(@RequestBody PreviewRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return ReturnResult.illegal("url 不能为空");
        }

        SpiderTaskDefinition taskDef = new SpiderTaskDefinition();
        if (request.downloaderType() != null && !request.downloaderType().isBlank()) {
            taskDef.setDownloader(request.downloaderType());
        }

        try {
            SpiderPreviewResult result = spiderToolkit.preview(request.url(), taskDef);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.illegal("预览失败: " + e.getMessage());
        }
    }

    // ── B48 POST /v1/spider/test-selector ────────────────────────────────────

    /**
     * POST /v1/spider/test-selector — 测试选择器匹配结果。
     *
     * <p>调用 {@link SpiderToolkit#testSelector(String, String, SpiderSelectorType)} 对目标页面
     * 执行选择器匹配，返回匹配到的元素列表。</p>
     */
    @PostMapping("/test-selector")
    public ReturnResult<List<String>> testSelector(@RequestBody TestSelectorRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return ReturnResult.illegal("url 不能为空");
        }
        if (request.selector() == null || request.selector().isBlank()) {
            return ReturnResult.illegal("selector 不能为空");
        }

        SpiderSelectorType selectorType = SpiderSelectorType.of(request.selectorType());

        try {
            List<String> matched = spiderToolkit.testSelector(request.url(), request.selector(), selectorType);
            return ReturnResult.ok(matched);
        } catch (Exception e) {
            return ReturnResult.illegal("选择器测试失败: " + e.getMessage());
        }
    }

    // ── 请求体 ────────────────────────────────────────────────────────────────

    /**
     * 预览请求体。
     *
     * @param url            目标页面 URL
     * @param downloaderType 下载器类型（jsoup / httpclient / playwright），可选
     */
    record PreviewRequest(String url, String downloaderType) {}

    /**
     * 选择器测试请求体。
     *
     * @param url          目标页面 URL
     * @param selector     选择器表达式
     * @param selectorType 选择器类型（css / xpath / regex / json_path），默认 css
     */
    record TestSelectorRequest(String url, String selector, String selectorType) {}

    // ── B97 GET /v1/spider/capabilities ──────────────────────────────────────

    /**
     * GET /v1/spider/capabilities — 查询所有已注册的 SPI 实现列表。
     */
    @GetMapping("/capabilities")
    public ReturnResult<?> capabilities() {
        if (nodeExecutorRegistry == null) {
            return ReturnResult.ok(Map.of("nodeExecutors", List.of()));
        }
        var executors = nodeExecutorRegistry.getAll().entrySet().stream()
                .map(e -> Map.of(
                        "nodeType", e.getKey().name(),
                        "executorClass", e.getValue().getClass().getSimpleName()
                ))
                .collect(Collectors.toList());
        return ReturnResult.ok(Map.of(
                "nodeExecutors", executors,
                "urlStores", List.of("database", "memory"),
                "pipelines", List.of("database", "console", "file")
        ));
    }
}
