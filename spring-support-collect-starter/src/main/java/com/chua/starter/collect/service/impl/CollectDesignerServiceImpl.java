// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.browser.BrowserEngine;
import com.chua.common.support.browser.BrowserOptions;
import com.chua.common.support.browser.BrowserPage;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.crawler.support.SpiderFetcher;
import com.chua.crawler.support.fetcher.HttpFetcher;
import com.chua.crawler.support.model.SpiderRequest;
import com.chua.crawler.support.model.SpiderResponse;
import com.chua.starter.collect.engine.CollectBuiltinInvoker;
import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.engine.CollectExecutorRegistry;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.entity.CollectFlow;
import com.chua.starter.collect.job.CollectFlowJobHandler;
import com.chua.starter.collect.mapper.CollectFlowMapper;
import com.chua.starter.collect.mapper.CollectTaskMapper;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.service.CollectDesignerService;
import com.chua.starter.collect.service.CollectExecutionService;
import com.chua.starter.collect.service.CollectFlowService;
import com.chua.starter.collect.service.CollectRecordService;
import com.chua.starter.collect.support.CollectCronSupport;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.vo.CollectPreviewVO;
import com.chua.starter.collect.vo.CollectRuntimeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 设计器与运行时概览服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectDesignerServiceImpl implements CollectDesignerService {

    /**
     * 定时预览最多返回次数。
     */
    private static final int MAX_PREVIEW = 10;

    /**
     * 未删除条件值。
     */
    private static final Integer ALIVE = 0;

    /**
     * 启用调度条件值。
     */
    private static final Integer ENABLED = 1;

    /**
     * 节点执行器注册表。
     */
    private final CollectExecutorRegistry registry;

    /**
     * 平台配置。
     */
    private final CollectProperties properties;

    /**
     * 流程表访问。
     */
    private final CollectFlowMapper flowMapper;

    /**
     * 任务表访问。
     */
    private final CollectTaskMapper taskMapper;

    /**
     * 结果服务，取结果总数。
     */
    private final CollectRecordService recordService;

    /**
     * 执行服务，取本机执行中标识。
     */
    private final CollectExecutionService executionService;

    /**
     * 流程服务，取调度台账的登记情况。
     */
    private final CollectFlowService flowService;

    @Override
    public List<CollectShapeMeta> metas() {
        return registry.metas();
    }

    @Override
    public CollectRuntimeVO runtime() {
        CollectRuntimeVO vo = new CollectRuntimeVO();
        vo.setEnable(properties.isEnable());
        vo.setExecutorId(executionService.executorId());
        vo.setRunningTasks(executionService.runningCount());
        vo.setFlowCount(flowMapper.selectCount(Wrappers.<CollectFlow>lambdaQuery()
                .eq(CollectFlow::getCollectFlowIsDeleted, ALIVE)));
        vo.setEnabledFlowCount(flowMapper.selectCount(Wrappers.<CollectFlow>lambdaQuery()
                .eq(CollectFlow::getCollectFlowIsDeleted, ALIVE)
                .eq(CollectFlow::getCollectFlowStatus, ENABLED)));
        vo.setTaskCount(taskMapper.selectCount(null));
        vo.setRecordCount(recordService.countRecords(null));
        vo.setSchedulerGroup(CollectFlowJobHandler.JOB_GROUP);
        vo.setScheduledJobCount((long) flowService.scheduledJobCount());
        vo.setTaskTimeoutMs(properties.getTaskTimeoutMs());
        vo.setMaxRecordPerTask(properties.getMaxRecordPerTask());
        vo.setDefaultThreadCount(properties.getDefaultThreadCount());
        vo.setRequestTimeoutMs(properties.getRequestTimeoutMs());
        vo.setShapes(registry.metas());
        vo.setBuiltinFunctions(CollectBuiltinInvoker.builtinNames().stream().sorted().toList());
        return vo;
    }

    @Override
    public CollectPreviewVO previewFetch(String url, Integer timeoutMs) {
        return previewFetch(url, timeoutMs, false);
    }

    @Override
    public CollectPreviewVO previewFetch(String url, Integer timeoutMs, boolean render) {
        String target = url == null ? "" : url.trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("预览抓取需要填写 URL");
        }
        URI uri;
        try {
            uri = URI.create(target);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("URL 格式不正确: " + target);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("预览抓取只支持 http/https，收到: " + scheme);
        }
        if (blockedHost(uri.getHost())) {
            throw new IllegalArgumentException("出于安全考虑，预览抓取不允许访问内网或本机地址: " + uri.getHost());
        }
        int timeout = timeoutMs == null || timeoutMs <= 0 ? properties.getRequestTimeoutMs() : timeoutMs;
        if (render) {
            return renderFetch(target, timeout);
        }
        SpiderFetcher fetcher = ServiceProvider.of(SpiderFetcher.class).getNewExtension("http");
        if (fetcher == null) {
            fetcher = new HttpFetcher(timeout);
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("timeoutMs", timeout);
        attributes.put("userAgent", properties.getUserAgent());
        SpiderRequest request = SpiderRequest.builder()
                .url(target)
                .method("GET")
                .headers(new LinkedHashMap<>())
                .attributes(attributes)
                .build();
        try {
            SpiderResponse response = fetcher.fetch(request);
            String html = response.getContent() == null ? "" : response.getContent();
            log.info("[collect] 预览抓取 {} → {} ({} 字符)", target, response.getStatusCode(), html.length());
            return new CollectPreviewVO(response.getStatusCode(), response.getContentType(), html.length(), html);
        } catch (Exception ex) {
            throw new IllegalStateException("预览抓取失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 用真实浏览器渲染后取 DOM。
     *
     * <p>走 {@code BrowserEngine.cdp()}（common-starter 内置的轻量 CDP 引擎，只用 JDK，
     * 不引 Playwright/Selenium），这样点选器看到的是执行完 JS 之后的页面——
     * SPA 站点在 HTTP 直取下通常只有一个空壳 {@code <div id="app"></div>}。</p>
     *
     * <p>浏览器进程按次启停而不是常驻：预览是设计期的低频操作，常驻一个 Chromium 会白占
     * 内存，而它又不像采集任务那样有稳定的调用频率可以摊薄启动成本。</p>
     *
     * @param target  目标地址
     * @param timeout 超时毫秒
     * @return 渲染后的抓取结果
     */
    private CollectPreviewVO renderFetch(String target, int timeout) {
        BrowserOptions options = new BrowserOptions()
                .setHeadless(true)
                .setLaunchTimeout(Duration.ofMillis(timeout))
                .setCommandTimeout(Duration.ofMillis(timeout));
        String userAgent = properties.getUserAgent();
        if (userAgent != null && !userAgent.isBlank()) {
            options.setUserAgent(userAgent);
        }
        long started = System.currentTimeMillis();
        try (BrowserEngine engine = BrowserEngine.cdp();
             BrowserPage page = engine.openPage(options)) {
            page.navigate(target);
            String html = page.content();
            long cost = System.currentTimeMillis() - started;
            String body = html == null ? "" : html;
            log.info("[collect] 预览渲染 {} → {} 字符 ({}ms)", target, body.length(), cost);
            return new CollectPreviewVO(200, "text/html; charset=UTF-8", body.length(), body, true);
        } catch (Exception ex) {
            // 渲染失败时把原始响应一起带回去，让用户至少能看到页面内容，而不是一个空面板。
            // 但仍要报错：只退回内容而不报错，「点了渲染却拿到没渲染的页面」比直接失败更难排查。
            String fallbackHtml = "";
            try {
                fallbackHtml = previewFetch(target, timeout, false).html();
            } catch (RuntimeException ignored) {
                // 直取也失败：保持空正文，由下面的异常说明原因
            }
            log.warn("[collect] 预览渲染失败: {} - {}", target, ex.getMessage());
            throw new IllegalStateException("浏览器渲染失败: " + ex.getMessage()
                    + (fallbackHtml.isEmpty() ? "（HTTP 直取也未取到内容）"
                    : "（HTTP 直取返回 " + fallbackHtml.length() + " 字符，可在关闭渲染后查看）"), ex);
        }
    }

    /**
     * 判定预览抓取的目标是否落在外网之外。
     *
     * <p>这个接口会按调用方给的 URL 主动发请求，等于开了一个 SSRF 面：不拦的话可以拿它探
     * 内网端口与云元数据地址（169.254.169.254）。这里拦三类：域名白名单式后缀
     * （localhost/*.local/*.internal）、以及**域名解析后**的地址是否本机/内网/链路本地。</p>
     *
     * <p><b>已知局限</b>：解析与真正连接之间仍存在 DNS rebinding 的时间窗（解析到公网、
     * 连接时指回内网）。要彻底堵住得把校验下沉到连接的 socket 层，属独立改造。</p>
     *
     * @param host 目标主机名（已从 URL 取好）
     * @return true 表示必须拒绝
     */
    private static boolean blockedHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String name = host.trim().toLowerCase(Locale.ROOT);
        if (name.equals("localhost") || name.endsWith(".localhost")
                || name.endsWith(".local") || name.endsWith(".internal")) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(name)) {
                if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                    return true;
                }
                byte[] raw = address.getAddress();
                // fc00::/7 唯一本地地址：isSiteLocalAddress 只覆盖 IPv4 私网
                if (raw.length == 16 && (raw[0] & 0xFE) == 0xFC) {
                    return true;
                }
            }
        } catch (UnknownHostException ex) {
            // 解析不了就当不安全：宁可让用户改用能解析的地址，也不放行未知目标
            return true;
        }
        return false;
    }

    @Override
    public List<LocalDateTime> cronPreview(String cron, int times) {
        CollectCronSupport.validate(cron);
        int count = Math.min(Math.max(times, 1), MAX_PREVIEW);
        long[] next = CollectCronSupport.nextList(cron, System.currentTimeMillis(), count);
        List<LocalDateTime> preview = new ArrayList<>();
        for (long millis : next) {
            preview.add(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
        }
        return preview;
    }

    @Override
    public CollectDslDTO aiGenerate(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("请输入要生成的流程描述");
        }
        CollectProperties.Ai ai = properties.getAi();
        if (ai == null || !ai.isEnable()) {
            throw new IllegalStateException("AI 生成流程未启用（plugin.collect.ai.enable=false）");
        }
        String systemPrompt = buildAiSystemPrompt();
        CollectDslDTO dsl = parseAiDsl(callAiChat(ai, systemPrompt, input.trim()));
        try {
            // 与保存共用同一张图校验：非法图形在这里拦下，原因原样给用户
            return CollectJson.parse(flowService.normalizeDsl(dsl), CollectDslDTO.class);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("AI 生成的流程未通过校验：" + ex.getMessage());
        }
    }

    /**
     * 构造 AI 生成流程的系统提示词：DSL 结构 + 规则 + 实时节点元数据 + 结构示例。
     *
     * <p>节点清单取自 {@link #metas()}（注册表实时生成），第三方 SPI 追加节点类型后提示词自动跟随。</p>
     *
     * @return 系统提示词
     */
    private String buildAiSystemPrompt() {
        StringBuilder shapes = new StringBuilder();
        for (CollectShapeMeta meta : metas()) {
            shapes.append("- ").append(meta.getShape()).append("（").append(meta.getLabel()).append("）");
            if (meta.getDescription() != null && !meta.getDescription().isBlank()) {
                shapes.append("：").append(meta.getDescription().trim());
            }
            shapes.append("；参数：");
            for (CollectParamMeta param : meta.getParams()) {
                shapes.append(param.getName());
                if (param.getLabel() != null && !param.getLabel().isBlank()) {
                    shapes.append("(").append(param.getLabel()).append(")");
                }
                if (param.isRequired()) {
                    shapes.append("[必填]");
                }
                if (param.getDefaultValue() != null && !param.getDefaultValue().isBlank()) {
                    shapes.append("默认").append(param.getDefaultValue());
                }
                shapes.append(" ");
            }
            if (meta.isFieldEditor()) {
                shapes.append("；抽取字段 fields=[{name,parserType,expression,attribute,attrName,group,defaultValue}]");
            }
            shapes.append("\n");
        }
        return "你是数据采集平台的流程设计助手。根据用户需求生成可直接运行的采集流程 DSL，只输出一个 JSON 对象（不要 markdown 代码块、不要解释）。\n"
                + "\nJSON 结构：\n"
                + "{ \"name\": \"流程名\", \"threadCount\": 1, \"headers\": [], \"cookies\": [], \"variables\": [],\n"
                + "  \"nodes\": [ { \"id\": \"n1\", \"shape\": \"start\", \"name\": \"开始\", \"x\": 60, \"y\": 80, \"params\": [ {\"name\":" +
                    " \"urls\", \"value\": \"https://example.com/\"} ] } ],\n"
                + "  \"edges\": [ { \"id\": \"e1\", \"from\": \"n1\", \"to\": \"n2\", \"condition\": \"\", \"exceptionFlow\": \"0\" } ] }\n"
                + "\n规则：\n"
                + "1. 每个节点给出 id/shape/name/x/y；x 从 60 起每列 +160，y 从 80 起每行 +120；节点 id 用 n1..n9，边 id 用 e1..e9。\n"
                + "2. edges 的 from/to 必须是已存在节点 id；图须为 start 到 end 的连通 DAG，禁止环。\n"
                + "3. 只能用下面列出的节点类型与参数名（参数名必须完全一致）；[必填] 参数必须提供 value。\n"
                + "4. 用 ${变量名} 引用上游出参（request 默认出参 resp，其 JSON 解析对象为 respJson）。\n"
                + "5. 抽取数据用 extract：mode=list + rowExpression（如 items）或 mode=single；fields 逐列声明解析方式。\n"
                + "6. 录入业务数据必须配 database 节点（datasource=app，table 优先用用户给定的表名，用户没指定表名就按需求起一个业务表名，表不存在会自动创建，column:<字段>=${字段}）；只把结果留在采集平台查看/导出才" +
                    "用 output（采集平台）节点（写平台自身的采集结果表）。凡涉及入库、写表、存进业务表，都不能省略 database 节点。\n"
                + "\n节点类型与参数：\n" + shapes
                + "\n一个结构示例（接口分页数据 → 抽取 → 入库 → 结束）：\n"
                + "{\"name\":\"示例-GitHub 项目采集\",\"threadCount\":2,\"headers\":[],\"cookies\":[],\"variables\":[],\"nodes\":["
                + "{\"id\":\"n1\",\"shape\":\"start\",\"name\":\"种子\",\"x\":60,\"y\":80,\"params\":[{\"name\":\"urls\",\"value\":\"https://a" +
                    "pi.github.com/...\"}]},"
                + "{\"id\":\"n2\",\"shape\":\"request\",\"name\":\"调用接口\",\"x\":220,\"y\":80,\"params\":[{\"name\":\"url\",\"value\":\"${url" +
                    "}\"},{\"name\":\"method\",\"value\":\"GET\"},{\"name\":\"outputVariable\",\"value\":\"resp\"}]},"
                + "{\"id\":\"n3\",\"shape\":\"extract\",\"name\":\"抽取字段\",\"x\":380,\"y\":80,\"params\":[{\"name\":\"source\",\"value\":\"${" +
                    "respJson}\"},{\"name\":\"mode\",\"value\":\"list\"},{\"name\":\"rowExpression\",\"value\":\"items\"}],\"fields\":[{\"name" +
                    "\":\"title\",\"parserType\":\"JSONPATH\",\"expression\":\".name\"}]},"
                + "{\"id\":\"n4\",\"shape\":\"database\",\"name\":\"写入业务表\",\"x\":540,\"y\":80,\"params\":[{\"name\":\"source\",\"value\":\"" +
                    "${rows}\"},{\"name\":\"datasource\",\"value\":\"app\"},{\"name\":\"table\",\"value\":\"github_projects\"},{\"name\":\"col" +
                    "umn:title\",\"value\":\"${title}\"}]},"
                + "{\"id\":\"n5\",\"shape\":\"end\",\"name\":\"结束\",\"x\":700,\"y\":80,\"params\":[]}],"
                + "\"edges\":[{\"id\":\"e1\",\"from\":\"n1\",\"to\":\"n2\",\"condition\":\"\",\"exceptionFlow\":\"0\"},{\"id\":\"e2\",\"from" +
                    "\":\"n2\",\"to\":\"n3\",\"condition\":\"\",\"exceptionFlow\":\"0\"},{\"id\":\"e3\",\"from\":\"n3\",\"to\":\"n4\",\"condit" +
                    "ion\":\"\",\"exceptionFlow\":\"0\"},{\"id\":\"e4\",\"from\":\"n4\",\"to\":\"n5\",\"condition\":\"\",\"exceptionFlow\":\"0" +
                    "\"}]}";
    }

    /**
     * 调用 ai-starter 的 OpenAI 兼容对话接口（同机回环 + 本地信任头放行）。
     *
     * @param ai           AI 配置
     * @param systemPrompt 系统提示词
     * @param userInput    用户需求描述
     * @return 模型输出正文
     */
    private String callAiChat(CollectProperties.Ai ai, String systemPrompt, String userInput) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", ai.getModel());
        payload.put("stream", false);
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userInput)));
        String responseBody;
        int connectTimeoutMs = Math.min(ai.getTimeoutMs(), 15000);
        try {
            ClientResponse response = HttpClientFactory.of(ai.getBaseUrl())
                    .path("/v2/v1/chat/completions")
                    .json()
                    .header("User-Agent", "curl/8.0")
                    .header(ai.getTrustHeader(), ai.getTrustValue())
                    .body(CollectJson.toJson(payload))
                    .connectTimeout(connectTimeoutMs)
                    .readTimeout(ai.getTimeoutMs())
                    .post();
            responseBody = response.getBodyString();
            if (response.getStatusCode() != 200) {
                throw new IllegalStateException("AI 服务返回 HTTP " + response.getStatusCode() + "：" + abbreviate(responseBody));
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("调用 AI 服务失败（" + ai.getBaseUrl() + "）:" + ex.getMessage(), ex);
        }
        Map<String, Object> body = CollectJson.parseMap(responseBody);
        Object choices = body.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("AI 服务未返回 choices：" + abbreviate(responseBody));
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("AI 服务返回结构异常：" + abbreviate(responseBody));
        }
        Object content = message.get("content");
        if (content == null || content.toString().isBlank()) {
            throw new IllegalStateException("AI 服务返回了空内容（检查 ai-starter 是否已配置可用模型）");
        }
        return content.toString();
    }

    /**
     * 从模型输出中解析流程 DSL（容忍 markdown 代码块与前后说明文字）。
     *
     * @param content 模型输出正文
     * @return DSL 入参
     */
    private CollectDslDTO parseAiDsl(String content) {
        String json = content.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("AI 输出里找不到 JSON：" + abbreviate(json));
        }
        json = json.substring(start, end + 1);
        try {
            return CollectJson.parse(json, CollectDslDTO.class);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("AI 输出不是合法流程 DSL：" + ex.getMessage());
        }
    }

    /**
     * 截断错误消息里的长文本。
     *
     * @param text 原文
     * @return 截断后的文本
     */
    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }
}