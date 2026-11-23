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
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * 解析配置文件为供应商草稿清单。
     *
     * @param request 来源与文件清单
     * @return 供应商草稿清单（可能为空：该来源没有可导入的配置）
     */
    @PostMapping("/parse")
    public ApiResult<List<ChannelImportEntry>> parse(@RequestBody ImportParseRequest request) {
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
