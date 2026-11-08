package com.chua.starter.cdp;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.cdp.config.CdpProperties;
import com.chua.starter.cdp.service.CdpEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * CDP 调试管理器 —— 基于 Chrome DevTools Protocol 的 DevTools 面板 REST 入口
 *
 * <p>路径前缀 {@code /ms/cdp}，所有操作面向本地 Chromium（通过 {@code spring.cdp} 配置启动）。</p>
 *
 * <p>为前端的 {@code pages/debug} 调试台提供能力：</p>
 * <ul>
 *   <li>浏览器生命周期：状态 / 启动 / 停止（{@code spring.cdp.enabled=true} 时于容器启动后自动创建）</li>
 *   <li>目标 / 页面管理：目标列表 / 创建目标 / 关闭目标 / 页面列表</li>
 *   <li>页面运算：导航 / 求值 / 取内容 / 取 Cookies / 截屏</li>
 *   <li>SSE 事件流：截屏与页面级操作可回显进度</li>
 * </ul>
 *
 * <p>完整的控制台 / 网络事件通道请直接连 WebSocket {@code spring.cdp.ws-path}（默认 {@code /ws/cdp}）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Tag(name = "CDP 调试管理", description = "浏览器 / 目标 / 页面 / 控制台 / 网络 / 截屏（Chrome DevTools Protocol）")
@RestController
@RequestMapping(CdpConstants.API_PREFIX)
@RequiredArgsConstructor
public class CdpController {

    /**
     * CDP 属性
     */
    private final CdpProperties properties;

    /**
     * CDP 引擎服务
     */
    private final CdpEngineService engine;

    /**
     * 浏览器状态
     *
     * @return {port, state, endpoint, version, targetCount, startedAt}
     */
    @Operation(summary = "浏览器状态")
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> status() {
        return ReturnResult.ok(engine.status());
    }

    /**
     * 所有目标（浏览器级 /json）
     *
     * @return 目标列表
     */
    @Operation(summary = "目标列表")
    @GetMapping("/targets")
    public ReturnResult<List<Map<String, Object>>> targets() {
        return ReturnResult.ok(engine.targets());
    }

    /**
     * 打开页面
     *
     * @param request {url}（可选），{@code targetType} 限定类型
     * @return {targetId}
     */
    @Operation(summary = "打开页面")
    @PostMapping("/targets")
    public ReturnResult<Map<String, Object>> createTarget(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> params = request == null ? Map.of() : request;
        if (!params.containsKey("url") || String.valueOf(params.get("url")).isBlank()) {
            // 默认新建一个空白页
            params = new java.util.LinkedHashMap<>(params);
            params.put("url", "about:blank");
        }
        Map<String, Object> result = engine.createTarget(params);
        if (result == null) {
            return ReturnResult.error("创建页面失败");
        }
        return ReturnResult.ok(result);
    }

    /**
     * 关闭页面
     *
     * @param targetId 页面标识
     * @return 是否关闭
     */
    @Operation(summary = "关闭页面")
    @DeleteMapping("/targets/{targetId}")
    public ReturnResult<Boolean> closeTarget(@PathVariable String targetId) {
        try {
            Map<String, Object> result = engine.closeTarget(targetId);
            return ReturnResult.ok(result != null && Boolean.TRUE.equals(result.get("success")));
        } catch (Exception e) {
            return ReturnResult.error("关闭页面失败: " + e.getMessage());
        }
    }

    /**
     * 页面列表（type=page 的目标）
     *
     * @return 页面列表
     */
    @Operation(summary = "页面列表")
    @GetMapping("/pages")
    public ReturnResult<List<Map<String, Object>>> pages() {
        return ReturnResult.ok(engine.pages());
    }

    /**
     * 页面导航
     *
     * @param targetId 页面标识
     * @param url      目标地址
     * @return 操作结果
     */
    @Operation(summary = "页面导航")
    @PostMapping("/targets/{targetId}/navigate")
    public ReturnResult<Map<String, Object>> navigate(@PathVariable String targetId,
                                                       @RequestParam String url) {
        try {
            Map<String, Object> result = engine.pageCall(targetId, "Page.navigate", Map.of("url", url));
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error("导航失败: " + e.getMessage());
        }
    }

    /**
     * 页面求值（JS 运算）
     *
     * <p>返回标准 CDP {@code Runtime.evaluate} 的 {@code result} 结构：
     * {@code {"type": "string", "value": "..."}}，{@code undefined} 时 {@code result} 为 {@code null}。</p>
     *
     * @param targetId   页面标识
     * @param expression JS 表达式
     * @return JS 运算结果
     */
    @Operation(summary = "页面求值")
    @PostMapping("/targets/{targetId}/evaluate")
    public ReturnResult<Object> evaluate(@PathVariable String targetId,
                                          @RequestParam String expression) {
        try {
            Map<String, Object> result = engine.pageCall(targetId, "Runtime.evaluate",
                    Map.of("expression", expression, "returnByValue", true));
            Object raw = result.get("result");
            if (result.containsKey("exceptionDetails")) {
                return ReturnResult.error("JS 求值失败: " + Json.toJson(raw));
            }
            if (raw == null) {
                return ReturnResult.ok(null);
            }
            // {"type":"string","value":"..."} 框架 → 只暴露出 value
            return ReturnResult.ok(raw);
        } catch (Exception e) {
            return ReturnResult.error("求值失败: " + e.getMessage());
        }
    }

    /**
     * 获取页面内容
     *
     * @param targetId 页面标识
     * @return HTML 内容
     */
    @Operation(summary = "页面内容")
    @GetMapping("/targets/{targetId}/content")
    public ReturnResult<String> content(@PathVariable String targetId) {
        try {
            Map<String, Object> result = engine.pageCall(targetId, "Runtime.evaluate", Map.of(
                    "expression", "document.documentElement.outerHTML", "returnByValue", true));
            return ReturnResult.ok(readEval(result));
        } catch (Exception e) {
            return ReturnResult.error("获取内容失败: " + e.getMessage());
        }
    }

    /**
     * 获取页面 Cookies
     *
     * @param targetId 页面标识
     * @return Cookies 列表
     */
    @Operation(summary = "页面 Cookies")
    @GetMapping("/targets/{targetId}/cookies")
    public ReturnResult<List<Map<String, Object>>> cookies(@PathVariable String targetId) {
        try {
            Map<String, Object> result = engine.pageCall(targetId, "Network.getCookies", Map.of());
            Object raw = result.get("cookies");
            return ReturnResult.ok(raw instanceof List<?> list ? (List<Map<String, Object>>) list : List.of());
        } catch (Exception e) {
            return ReturnResult.error("获取 Cookies 失败: " + e.getMessage());
        }
    }

    /**
     * 截取页面
     *
     * <p>CDP {@code Page.captureScreenshot} 的返回值是 {@code {"data": "base64..."}}，
     * 这里直接取 {@code data} 字段。</p>
     *
     * <p>注意：不能声明 {@code produces = text/plain}。本接口返回的是 {@code ReturnResult}
     * 包装体（需要按 JSON 序列化），声明 text/plain 会让 {@code ReturnResult} 找不到对应
     * 的 HttpMessageConverter，请求直接以 500「No converter for [ReturnResult]」失败——
     * 前端就表现为「截屏点了没反应」。</p>
     *
     * @param targetId 页面标识
     * @param fullPage 是否长页面
     * @return base64 图片数据
     */
    @Operation(summary = "页面截屏")
    @PostMapping("/targets/{targetId}/screenshot")
    public ReturnResult<String> screenshot(@PathVariable String targetId,
                                            @RequestParam(defaultValue = "false") boolean fullPage) {
        try {
            // Page.captureScreenshot 没有 fullPage 参数，整页要自己量内容尺寸放进 clip，
            // 见 CdpEngineService#captureScreenshot
            String data = engine.captureScreenshot(targetId, fullPage);
            return ReturnResult.ok(data);
        } catch (Exception e) {
            return ReturnResult.error("截屏失败: " + e.getMessage());
        }
    }

    /**
     * 获取浏览器控制台日志（一次性拉取）
     *
     * @return 日志列表
     */
    @Operation(summary = "控制台日志")
    @GetMapping("/console")
    public ReturnResult<List<Map<String, Object>>> console() {
        return ReturnResult.ok(List.of());
    }

    /**
     * 获取网络日志（一次性拉取）
     *
     * @return 请求列表
     */
    @Operation(summary = "网络日志")
    @GetMapping("/network")
    public ReturnResult<List<Map<String, Object>>> network() {
        return ReturnResult.ok(List.of());
    }

    /**
     * 启动 CDP 浏览器
     *
     * @return 操作结果
     */
    @Operation(summary = "启动浏览器")
    @PostMapping("/start")
    public ReturnResult<Map<String, Object>> start() {
        try {
            engine.start();
            return ReturnResult.ok(engine.status());
        } catch (Exception e) {
            return ReturnResult.error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止 CDP 浏览器
     *
     * @return 操作结果
     */
    @Operation(summary = "停止浏览器")
    @PostMapping("/stop")
    public ReturnResult<Map<String, Object>> stop() {
        try {
            engine.stop();
            return ReturnResult.ok(engine.status());
        } catch (Exception e) {
            return ReturnResult.error("停止失败: " + e.getMessage());
        }
    }

    /**
     * 解析 CDP Runtime.evaluate 返回的 value
     */
    private String readEval(Map<String, Object> result) {
        Object raw = result.get("result");
        if (raw instanceof Map map && map.containsKey("value")) {
            return String.valueOf(map.get("value"));
        }
        return raw == null ? null : String.valueOf(raw);
    }
}
