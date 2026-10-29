package com.chua.starter.monitor.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * CSV 批量导入（快速模式）预览结果：真实读取文件前若干行。
 *
 * <p>供 {@code BulkImportDialog} 预览确认页使用——数据必须来自文件真实内容，
 * 不允许前端 mock。表头用于字段映射匹配，前几行用于人工核对列序与样例值。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Data
@Schema(description = "CSV 批量导入预览结果")
public class BulkPreviewVO {

    /**
     * 表头（源文件列名）
     */
    @Schema(description = "表头（源文件列名）")
    private List<String> headers;

    /**
     * 前若干行样例（Map<列名, 值>），值统一为字符串
     */
    @Schema(description = "前若干行样例数据")
    private List<Map<String, String>> rows;

    /**
     * 数据总行数（不含表头）
     */
    @Schema(description = "数据总行数（不含表头）")
    private long total;
}