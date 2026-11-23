package com.chua.starter.account.web;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.spi.ChannelConfigImportParser;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 「从配置导入供应商」解析入口。
 *
 * <p>前端把用户选中的配置文件以 base64 上传，本控制器按
 * {@code source} 分发到 {@link ChannelConfigImportParser} SPI 实现解析，
 * 返回供应商草稿清单。是否创建供应商由前端确认后走既有
 * {@code /api/providers} 管理接口，本接口不落库。</p>
 *
 * <p>请求体：{@code { source, files: [{ name, dataBase64 }] }}；
 * 响应：{@code ApiResult<List<ChannelImportEntry>>}。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@ApiReturnFormatIgnore
@Slf4j
@RestController
@RequestMapping("/api/providers/import")
public class ChannelConfigImportController {

    /** 单次解析的字节总量上限（防止一次性上传超大文件撑爆内存） */
    private static final int MAX_TOTAL_BYTES = 128 * 1024 * 1024;

    /**
     * 本端点专用 JSON 读取器。请求体含 base64 大文件（如 ~15MB 的 cc-switch 库，
     * 序列化后约 2000 万字符），会撞上 Jackson 默认 maxStringLength=2000 万；
     * 全局 ObjectMapper（common-starter JacksonConfiguration 自建）保持默认约束，
     * 故在此用放宽后的独立 mapper 解析请求体，不牵动其它接口的 JSON 行为。
     */
    private static final ObjectMapper REQUEST_MAPPER = new ObjectMapper();

    static {
        // JsonFactory#setStreamReadConstraints 自 Jackson 2.15 起可用，构建后再放宽上限
        REQUEST_MAPPER.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(200_000_000).build());
    }

    /**
     * 解析配置文件为供应商草稿清单。
     *
     * <p>请求体以原始字节接收、由本端点专用 mapper 解析：base64 大文件会超过
     * 全局 ObjectMapper 的默认字符串上限，走 {@code @RequestBody ImportParseRequest}
     * 会在消息转换阶段直接 500。</p>
     *
     * @param body 原始 JSON 请求体（{@code { source, files: [{ name, dataBase64 }] }}）
     * @return 供应商草稿清单（可能为空：该来源没有可导入的配置）
     */
    @PostMapping("/parse")
    public ApiResult<List<ChannelImportEntry>> parse(@RequestBody byte[] body) {
        if (body == null || body.length == 0) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "缺少请求体");
        }
        // base64 约膨胀 4/3，加 1MB JSON 结构余量做粗筛；精确的 128MB 校验在解码后进行
        if (body.length > MAX_TOTAL_BYTES / 3 * 4 + 1024 * 1024) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "上传内容过大（上限 128MB）");
        }
        ImportParseRequest request;
        try {
            request = REQUEST_MAPPER.readValue(new String(body, StandardCharsets.UTF_8), ImportParseRequest.class);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请求体不是有效的 JSON: " + e.getMessage());
        }
        if (request == null || request.getSource() == null || request.getSource().trim().isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "缺少解析来源 source");
        }
        String source = request.getSource().trim();

        List<ChannelImportFile> files = decodeFiles(request);
        if (files.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有可解析的文件内容");
        }

        ServiceProvider<ChannelConfigImportParser> provider = ServiceProvider.of(ChannelConfigImportParser.class);
        if (!provider.isSupport(source)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "不支持的导入来源: " + source);
        }
        ChannelConfigImportParser parser = provider.getIfPresent(source)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "不支持的导入来源: " + source));

        try {
            List<ChannelImportEntry> entries = parser.parse(files);
            return ApiResult.ok(entries == null ? List.of() : entries);
        } catch (IllegalArgumentException e) {
            // 配置形态不符 / 文件损坏等用户可修正的问题
            throw new ApiException(ApiErrorCode.INVALID_PARAM, e.getMessage());
        } catch (Exception e) {
            log.error("配置导入解析失败: source={}", source, e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                    "解析失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * base64 文件载荷 → 字节文件清单（Mime 解码容忍换行）。
     */
    private List<ChannelImportFile> decodeFiles(ImportParseRequest request) {
        List<ChannelImportFile> files = new ArrayList<>();
        if (request.getFiles() == null) {
            return files;
        }
        long total = 0;
        for (ImportFilePayload payload : request.getFiles()) {
            if (payload == null || payload.getDataBase64() == null || payload.getDataBase64().isEmpty()) {
                continue;
            }
            byte[] bytes;
            try {
                bytes = Base64.getMimeDecoder().decode(payload.getDataBase64());
            } catch (IllegalArgumentException e) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM,
                        "文件 " + payload.getName() + " 不是有效的 base64 内容");
            }
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "上传内容过大（上限 128MB）");
            }
            files.add(new ChannelImportFile(payload.getName() == null ? "" : payload.getName(), bytes));
        }
        return files;
    }

    /** 解析请求体 */
    @Data
    public static class ImportParseRequest {

        /** 来源标识（SPI 扩展名：opencode / cc-switch / claude-settings / env / magpie / cursor / gemini-cli / codex-cli） */
        @JsonProperty("source")
        private String source;

        /** 上传文件清单 */
        @JsonProperty("files")
        private List<ImportFilePayload> files;
    }

    /** 单个上传文件（base64 字节） */
    @Data
    public static class ImportFilePayload {

        /** 文件名（按名查找，如 config.toml / auth.json） */
        @JsonProperty("name")
        private String name;

        /** 文件字节（base64） */
        @JsonProperty("dataBase64")
        private String dataBase64;
    }
}
