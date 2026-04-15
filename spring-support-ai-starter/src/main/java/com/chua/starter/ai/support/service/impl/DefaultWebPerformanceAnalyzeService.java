package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.chat.ChatScope;
import com.chua.starter.ai.support.model.WebPerformanceAnalyzeRequest;
import com.chua.starter.ai.support.model.WebPerformanceAnalyzeResult;
import com.chua.starter.ai.support.properties.AiPerformanceProperties;
import com.chua.starter.ai.support.properties.AiProperties;
import com.chua.starter.ai.support.properties.ProviderProperties;
import com.chua.starter.ai.support.service.WebPerformanceAnalyzeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网站性能分析默认实现。
 * <p>
 * 当前实现使用轻量 HTTP 采样，可在无浏览器依赖下快速提供可用指标。
 *
 * @author CH
 * @since 2026/04/14
 */
@Slf4j
public class DefaultWebPerformanceAnalyzeService implements WebPerformanceAnalyzeService {

    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final String DEFAULT_UA = "SpringSupport-AI-Performance/1.0";

    private final AiProperties aiProperties;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final HttpClient httpClient;

    public DefaultWebPerformanceAnalyzeService(AiProperties aiProperties, ObjectProvider<ChatClient> chatClientProvider) {
        this.aiProperties = aiProperties;
        this.chatClientProvider = chatClientProvider;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public WebPerformanceAnalyzeResult analyze(WebPerformanceAnalyzeRequest request) {
        long start = System.currentTimeMillis();
        AiPerformanceProperties performance = aiProperties.getPerformance() == null ? new AiPerformanceProperties() : aiProperties.getPerformance();
        if (!performance.isEnabled()) {
            throw new IllegalStateException("Website performance analyze is disabled");
        }

        URI target = parseAndValidateUrl(request == null ? null : request.getUrl());
        int sampleCount = bounded(
                request == null ? null : request.getSampleCount(),
                performance.getSampleCount(),
                1,
                20);
        int sampleIntervalMs = bounded(
                request == null ? null : request.getSampleIntervalMs(),
                performance.getSampleIntervalMs(),
                0,
                10_000);
        int connectTimeoutMs = bounded(
                request == null ? null : request.getConnectTimeoutMs(),
                performance.getConnectTimeoutMs(),
                500,
                120_000);
        int requestTimeoutMs = bounded(
                request == null ? null : request.getRequestTimeoutMs(),
                performance.getRequestTimeoutMs(),
                1000,
                300_000);
        boolean includeSnapshot = request == null || request.getIncludeSnapshot() == null || request.getIncludeSnapshot();
        boolean aiAdviceEnabled = (request == null || request.getAiAdviceEnabled() == null)
                ? performance.isAiAdviceEnabled()
                : request.getAiAdviceEnabled();
        String userAgent = hasText(request == null ? null : request.getUserAgent()) ? request.getUserAgent().trim() : DEFAULT_UA;

        List<CollectedSample> collectedSamples = new ArrayList<>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            collectedSamples.add(collectSample(target, i + 1, connectTimeoutMs, requestTimeoutMs, userAgent, includeSnapshot, performance));
            if (sampleIntervalMs > 0 && i < sampleCount - 1) {
                try {
                    Thread.sleep(sampleIntervalMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        List<WebPerformanceAnalyzeResult.SampleMetric> samples = collectedSamples.stream()
                .map(CollectedSample::metric)
                .toList();
        WebPerformanceAnalyzeResult.SummaryMetric summary = buildSummary(samples);
        WebPerformanceAnalyzeResult.TraceOverview trace = buildTrace(samples);
        WebPerformanceAnalyzeResult.SnapshotOverview snapshot = includeSnapshot ? buildSnapshot(collectedSamples, performance) : null;
        List<String> recommendations = buildRuleRecommendations(summary);

        boolean aiEnhanced = false;
        String aiAdvice = null;
        if (aiAdviceEnabled && aiProperties.getLlm() != null && aiProperties.getLlm().isEnabled() && hasAvailableCredential(aiProperties)) {
            aiAdvice = tryGenerateAiAdvice(target.toString(), summary, recommendations);
            aiEnhanced = hasText(aiAdvice);
        }

        long end = System.currentTimeMillis();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestedSampleCount", sampleCount);
        metadata.put("collector", "http-lightweight");
        metadata.put("snapshotIncluded", includeSnapshot);
        metadata.put("aiAdviceEnabled", aiAdviceEnabled);
        metadata.put("aiCredentialDetected", hasAvailableCredential(aiProperties));

        return WebPerformanceAnalyzeResult.builder()
                .url(target.toString())
                .collector("http-lightweight")
                .mode("sampling")
                .startTime(start)
                .endTime(end)
                .costTime(end - start)
                .samples(samples)
                .summary(summary)
                .trace(trace)
                .snapshot(snapshot)
                .recommendations(recommendations)
                .aiAdvice(aiAdvice)
                .aiEnhanced(aiEnhanced)
                .metadata(metadata)
                .build();
    }

    private CollectedSample collectSample(URI uri,
                                          int index,
                                          int connectTimeoutMs,
                                          int requestTimeoutMs,
                                          String userAgent,
                                          boolean includeSnapshot,
                                          AiPerformanceProperties performance) {
        long requestStart = System.currentTimeMillis();
        Integer statusCode = null;
        String contentType = null;
        long responseBytes = 0L;
        long headerLatencyMs = 0L;
        long ttfbMs = 0L;
        long downloadMs = 0L;
        long totalCostMs = 0L;
        String errorMessage = null;
        String preview = null;

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .header("User-Agent", userAgent)
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            long headersAt = System.currentTimeMillis();
            headerLatencyMs = Math.max(0L, headersAt - requestStart);
            statusCode = response.statusCode();
            contentType = response.headers().firstValue("Content-Type").orElse(null);

            long firstByteAt;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream inputStream = response.body()) {
                byte[] first = inputStream.readNBytes(1024);
                firstByteAt = System.currentTimeMillis();
                if (first.length > 0) {
                    output.write(first);
                }

                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            }

            long endAt = System.currentTimeMillis();
            responseBytes = output.size();
            ttfbMs = Math.max(0L, firstByteAt - requestStart);
            downloadMs = Math.max(0L, endAt - firstByteAt);
            totalCostMs = Math.max(0L, endAt - requestStart);

            if (includeSnapshot) {
                int previewLimit = bounded(null, performance.getSnapshotPreviewLength(), 100, 5_000);
                preview = truncateUtf8(output.toString(StandardCharsets.UTF_8), previewLimit);
            }
        } catch (Exception e) {
            totalCostMs = Math.max(0L, System.currentTimeMillis() - requestStart);
            errorMessage = e.getMessage();
        }

        WebPerformanceAnalyzeResult.SampleMetric metric = WebPerformanceAnalyzeResult.SampleMetric.builder()
                .index(index)
                .statusCode(statusCode)
                .contentType(contentType)
                .responseBytes(responseBytes)
                .headerLatencyMs(headerLatencyMs)
                .ttfbMs(ttfbMs)
                .downloadMs(downloadMs)
                .totalCostMs(totalCostMs)
                .success(errorMessage == null && statusCode != null && statusCode >= 200 && statusCode < 500)
                .errorMessage(errorMessage)
                .build();
        return new CollectedSample(metric, preview);
    }

    private WebPerformanceAnalyzeResult.SummaryMetric buildSummary(List<WebPerformanceAnalyzeResult.SampleMetric> samples) {
        if (samples == null || samples.isEmpty()) {
            return WebPerformanceAnalyzeResult.SummaryMetric.builder()
                    .sampleCount(0)
                    .successCount(0)
                    .successRate(0D)
                    .minCostMs(0L)
                    .maxCostMs(0L)
                    .avgCostMs(0D)
                    .p95CostMs(0D)
                    .avgTtfbMs(0D)
                    .avgPayloadBytes(0L)
                    .build();
        }

        long successCount = samples.stream().filter(WebPerformanceAnalyzeResult.SampleMetric::isSuccess).count();
        List<Long> totalCosts = samples.stream().map(WebPerformanceAnalyzeResult.SampleMetric::getTotalCostMs).sorted().toList();
        long min = totalCosts.getFirst();
        long max = totalCosts.getLast();
        double avgCost = samples.stream().mapToLong(WebPerformanceAnalyzeResult.SampleMetric::getTotalCostMs).average().orElse(0D);
        double avgTtfb = samples.stream().mapToLong(WebPerformanceAnalyzeResult.SampleMetric::getTtfbMs).average().orElse(0D);
        long avgPayload = Math.round(samples.stream().mapToLong(WebPerformanceAnalyzeResult.SampleMetric::getResponseBytes).average().orElse(0D));
        double p95 = percentile(totalCosts, 0.95D);

        Integer lastStatus = samples.getLast().getStatusCode();
        return WebPerformanceAnalyzeResult.SummaryMetric.builder()
                .sampleCount(samples.size())
                .successCount((int) successCount)
                .successRate(samples.isEmpty() ? 0D : (successCount * 100D / samples.size()))
                .minCostMs(min)
                .maxCostMs(max)
                .avgCostMs(avgCost)
                .p95CostMs(p95)
                .avgTtfbMs(avgTtfb)
                .avgPayloadBytes(avgPayload)
                .lastStatusCode(lastStatus)
                .build();
    }

    private WebPerformanceAnalyzeResult.TraceOverview buildTrace(List<WebPerformanceAnalyzeResult.SampleMetric> samples) {
        if (samples == null || samples.isEmpty()) {
            return WebPerformanceAnalyzeResult.TraceOverview.builder()
                    .traceType("lightweight-http")
                    .stages(List.of())
                    .build();
        }

        WebPerformanceAnalyzeResult.SampleMetric anchor = samples.stream()
                .sorted((a, b) -> Long.compare(a.getTotalCostMs(), b.getTotalCostMs()))
                .skip(samples.size() / 2)
                .findFirst()
                .orElse(samples.getFirst());
        List<WebPerformanceAnalyzeResult.TraceStage> stages = new ArrayList<>();
        stages.add(WebPerformanceAnalyzeResult.TraceStage.builder().name("request+headers").durationMs(anchor.getHeaderLatencyMs()).build());
        stages.add(WebPerformanceAnalyzeResult.TraceStage.builder().name("ttfb").durationMs(anchor.getTtfbMs()).build());
        stages.add(WebPerformanceAnalyzeResult.TraceStage.builder().name("download").durationMs(anchor.getDownloadMs()).build());
        return WebPerformanceAnalyzeResult.TraceOverview.builder()
                .traceType("lightweight-http")
                .stages(stages)
                .build();
    }

    private WebPerformanceAnalyzeResult.SnapshotOverview buildSnapshot(List<CollectedSample> samples, AiPerformanceProperties performance) {
        if (samples == null || samples.isEmpty()) {
            return null;
        }

        CollectedSample target = null;
        for (CollectedSample sample : samples) {
            if (sample != null && sample.metric != null && sample.metric.isSuccess() && hasText(sample.preview())) {
                target = sample;
                break;
            }
        }
        if (target == null) {
            target = samples.getFirst();
        }
        if (target == null || target.metric == null) {
            return null;
        }

        String preview = truncateUtf8(target.preview, bounded(null, performance.getSnapshotPreviewLength(), 100, 5_000));
        return WebPerformanceAnalyzeResult.SnapshotOverview.builder()
                .title(extractHtmlTitle(preview))
                .contentType(target.metric.getContentType())
                .contentLength(target.metric.getResponseBytes())
                .preview(preview)
                .build();
    }

    private List<String> buildRuleRecommendations(WebPerformanceAnalyzeResult.SummaryMetric summary) {
        if (summary == null) {
            return List.of("无法生成建议：缺少有效采样数据");
        }
        List<String> recommendations = new ArrayList<>();
        if (summary.getSuccessRate() < 95D) {
            recommendations.add("可用性偏低，建议先检查网关/上游依赖与超时重试策略。");
        }
        if (summary.getAvgTtfbMs() > 800D) {
            recommendations.add("TTFB 偏高，建议排查 DNS、连接复用与后端首包计算路径。");
        }
        if (summary.getP95CostMs() > 3000D) {
            recommendations.add("P95 延迟偏高，建议增加慢路径日志并定位尾延迟热点。");
        }
        if (summary.getAvgPayloadBytes() > 1_500_000L) {
            recommendations.add("响应体较大，建议启用压缩、按需字段下发与静态资源分片缓存。");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("整体指标稳定，可继续用 CDP/Trace 对关键交互做帧率与长任务专项分析。");
        }
        return recommendations;
    }

    private String tryGenerateAiAdvice(String url,
                                       WebPerformanceAnalyzeResult.SummaryMetric summary,
                                       List<String> ruleRecommendations) {
        try {
            ChatClient chatClient = chatClientProvider.getIfAvailable();
            if (chatClient == null) {
                return null;
            }
            String prompt = buildAiPrompt(url, summary, ruleRecommendations);
            return chatClient.chat(ChatScope.builder()
                    .systemPrompt("你是资深 Web 性能工程师，请输出可执行、可验证、按优先级排序的优化建议。")
                    .input(prompt)
                    .temperature(0.2D)
                    .maxTokens(900)
                    .build()).getText();
        } catch (Exception e) {
            log.debug("[AI][性能分析]生成 AI 建议失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildAiPrompt(String url,
                                 WebPerformanceAnalyzeResult.SummaryMetric summary,
                                 List<String> ruleRecommendations) {
        StringBuilder builder = new StringBuilder();
        builder.append("目标站点: ").append(url).append('\n');
        builder.append("采样统计: ").append('\n');
        builder.append("- sampleCount=").append(summary.getSampleCount()).append('\n');
        builder.append("- successRate=").append(String.format("%.2f", summary.getSuccessRate())).append("%\n");
        builder.append("- avgCostMs=").append(String.format("%.2f", summary.getAvgCostMs())).append('\n');
        builder.append("- p95CostMs=").append(String.format("%.2f", summary.getP95CostMs())).append('\n');
        builder.append("- avgTtfbMs=").append(String.format("%.2f", summary.getAvgTtfbMs())).append('\n');
        builder.append("- avgPayloadBytes=").append(summary.getAvgPayloadBytes()).append('\n');
        builder.append("规则建议: ").append(String.join("；", ruleRecommendations)).append('\n');
        builder.append("请输出: 1) 根因假设 2) 优先级排序的优化项 3) 每项验证指标。");
        return builder.toString();
    }

    private URI parseAndValidateUrl(String url) {
        if (!hasText(url)) {
            throw new IllegalArgumentException("url is required");
        }
        URI uri = URI.create(url.trim());
        String schema = uri.getScheme();
        if (!"http".equalsIgnoreCase(schema) && !"https".equalsIgnoreCase(schema)) {
            throw new IllegalArgumentException("Only http/https url is supported");
        }
        if (!hasText(uri.getHost())) {
            throw new IllegalArgumentException("Invalid url host");
        }
        return uri;
    }

    private boolean hasAvailableCredential(AiProperties properties) {
        if (properties == null || properties.getProviders() == null || properties.getProviders().isEmpty()) {
            return false;
        }
        for (ProviderProperties provider : properties.getProviders().values()) {
            if (provider == null) {
                continue;
            }
            if (hasText(provider.getApiKey())
                    || hasText(provider.getAppKey())
                    || hasText(provider.getAppSecret())
                    || hasText(provider.getSecretKey())) {
                return true;
            }
        }
        return false;
    }

    private String extractHtmlTitle(String html) {
        if (!hasText(html)) {
            return null;
        }
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).replaceAll("\\s+", " ").trim();
    }

    private String truncateUtf8(String value, int maxLength) {
        if (!hasText(value)) {
            return value;
        }
        String normalized = value.replace('\0', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int bounded(Integer requestValue, Integer defaultValue, int min, int max) {
        int value = requestValue != null ? requestValue : (defaultValue == null ? min : defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    private double percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0D;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.getFirst();
        }
        double rank = percentile * (sortedValues.size() - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        if (low == high) {
            return sortedValues.get(low);
        }
        double weight = rank - low;
        return sortedValues.get(low) * (1 - weight) + sortedValues.get(high) * weight;
    }

    private record CollectedSample(WebPerformanceAnalyzeResult.SampleMetric metric, String preview) {
    }
}
